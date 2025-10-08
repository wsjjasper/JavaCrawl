Here’s a crisp, 12-month, fully-workable plan you can hand to your leads. I split it into (A) building blocks with success criteria & feasibility, (B) architecture guardrails, (C) migration & testing strategy, and (D) a month-by-month roadmap. I kept the examples anchored on exposures/limits because they’re the most sensitive for Credit Risk.

# A) Building blocks (with success criteria & feasibility)

| #  | Building block                                | What it is (scope)                                                                                                                                                                                                                   | Why now                                                                                  | Success criteria (measurable)                                                                                                                                                        | Feasibility                                                                                                                           |
| -- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- |
| 1  | **Semantic aggregation model**                | Canonical **dimensions** (party, LE, country, industry, segment, scenario, COB), **hierarchies**, and **measures** (inventory, treasury, collateral group stats, PE stats, exposures, limits). Stored as metadata tables (not code). | Eliminates duplicated logic; makes Spark/DB2 implementations converge on a single truth. | a) 100% of EOD measures expressed via metadata, not hard-coded. b) ≤1% drift in measure definitions month-over-month (tracked). c) Central dictionary adopted by ≥2 consuming teams. | **90%** (high)                                                                                                                        |
| 2  | **Aggregation definition registry**           | Config tables for *what to roll up where*: `AGG_DEFINITION`, `MEASURE_DEF`, `DERIVATION_RULE`, `HIERARCHY_DEF`, `OUTPUT_CONTRACT`.                                                                                                   | Declarative plan drives codegen/SQL/ Snowpark; simplifies onboarding.                    | a) ≥90% of aggregations run from registry. b) New aggregation onboard time ≤ 2 days.                                                                                                 | **85%**                                                                                                                               |
| 3  | **Snowflake execution layer**                 | Implementation patterns: set-based SQL, **Tasks** for EOD scheduling, optional **Dynamic Tables** for dependency graphing, and selective **Search Optimization/QAS** for hotspots.                                                   | Performance & consistency on target platform.                                            | a) EOD runtime ≤ **45–50 min** at P95. b) Regression parity ≥ **99.9%** on top-10 measures by materiality.                                                                           | **80%** (features are standard and GA: Tasks, Dynamic Tables, QAS/Search Optimization are well-documented). ([docs.snowflake.com][1]) |
| 4  | **Unified derivation rule library**           | Reusable UDFs/Snowpark functions for common derivations (e.g., “exposure at party+LE with netting X”, “industry fallback”, “country mapping overrides”).                                                                             | Removes copy-paste derivations scattered across codebases.                               | a) ≥20 shared functions cover ≥80% derivation use-cases. b) Unit test coverage ≥90%.                                                                                                 | **80%**                                                                                                                               |
| 5  | **Foundational data quality (DQ) & controls** | Lightweight, SQL-first checks: **completeness, conformance, reconciliations to limits**, threshold alerts, and late/dup detection. Persist results in `DQ_RULE`/`DQ_RESULT` with SLA tags; wire to Pager/Email.                      | You’re missing a DQ framework today; regulators will ask.                                | a) ≥30 critical DQ rules live (incl. exposure↔limit reconciliations). b) <0.1% unresolved DQ exceptions at T+1. c) Automated evidence pack.                                          | **75%**                                                                                                                               |
| 6  | **SLA & observability for batch**             | Run ledger (`RUN`, `STEP`, `OUTPUT_SNAPSHOT`), data volume & skew metrics, and **task/dynamic table event logging**.                                                                                                                 | Faster triage; auditability.                                                             | a) Time-to-detect failures < 5 min. b) Time-to-root-cause (MTTR) < 30 min.                                                                                                           | **85%** (Snowflake logs events for Tasks/Dynamic Tables). ([docs.snowflake.com][2])                                                   |
| 7  | **Lineage & catalog integration**             | Emit OpenLineage/metadata hooks from job runner; attach column/measure lineage; publish contracts to existing catalog.                                                                                                               | Transparency for risk & regulatory users.                                                | a) Lineage from source→measure visible for ≥90% of outputs.                                                                                                                          | **80%**                                                                                                                               |
| 8  | **Output contracts (“data products”)**        | Versioned, documented tables/views per consumer group with SLAs, schema stability & deprecation policy.                                                                                                                              | De-couples producers from consumers; reduces breakage.                                   | a) Zero breaking schema changes without 2-cycle deprecation. b) Consumer satisfaction ≥8/10 survey.                                                                                  | **85%**                                                                                                                               |
| 9  | **Cutover safety net**                        | Dual-run, diff harness, automated backfill, shadow writes, blue/green switch, rollback.                                                                                                                                              | Risk-free migration for regulatory datasets.                                             | a) ≥4 consecutive green EODs before cutover. b) Rollback ≤ 15 min.                                                                                                                   | **80%**                                                                                                                               |
| 10 | **Retirement & debt paydown**                 | Systematic decommission of DB2/SP/Spark duplicates; archive, doc, and tombstone.                                                                                                                                                     | Locks in the win.                                                                        | a) Retire ≥50% of duplicate SPs; ≥30% Spark jobs simplified or merged.                                                                                                               | **70%** (org/process risk)                                                                                                            |

> Feasibility key: High ≥80%, Medium 60–79%, Stretch <60%.

---

# B) Architecture guardrails (practical & opinionated)

1. **Declarative first**
   All aggregations are driven by **metadata**. The engine compiles definitions into SQL (or Snowpark) and executes via **Tasks** on a nightly EOD schedule. Where DAG-style dependencies help (e.g., build base exposures → rollups → limit checks), use **Dynamic Tables** with target freshness = EOD. ([docs.snowflake.com][1])

2. **Snowflake performance patterns**

* Warehouse right-sizing + auto-scaling; prune scans via partitioning logic & **Search Optimization** only on pinpointed columns (e.g., party_id, LE, COB). Optionally enable **Query Acceleration Service** for long-tail heavy joins during close. ([docs.snowflake.com][3])
* Prefer set-based SQL; keep interim materializations as transient tables; cache hot dims as temporary tables for join fan-out control.

3. **Reusable derivations**
   Centralize UDFs and **Snowpark** functions for shared math (EAD/EL/UL pivots, netting, FX normalization), versioned & unit-tested. ([docs.snowflake.com][4])

4. **Security & governance**
   Row/column policies, tags, and contract-level access; lineage hooks on publish; attach SLA tags to outputs (EOD_max_time, freshness_hours).

5. **Quality & controls**
   DQ rules codified as metadata; executed as SQL assertions pre-/post-aggregation; results stored and surfaced on a simple Ops dashboard; regulatory “evidence pack” generated daily.

---

# C) Migration & testing strategy (make it boring and safe)

**Inventory & dedupe:**

* Catalogue 100% of existing aggregation codepaths (DB2 SPs + Spark). Cluster them by measure and dimension grain; identify duplicates and “special derivations”.

**Golden parity harness:**

* For each measure (e.g., **ExposureByParty**, **LimitBreachesByLE**), define parity specs: grain, rounding, null/FX policy, tolerances. Create an automated comparator that produces row-level and measure-level diffs, with materiality scoring.

**Dual-run & shadow:**

* Run new Snowflake jobs nightly in parallel for ≥ 4–6 weeks. Only after **≥ 4 consecutive green EODs** (no material variances) do we switch the data contract to the new producer.

**Backfill & point-in-time:**

* Use time-travel/clone to backfill history and validate point-in-time views that downstreams rely on (BOD/EOD snapshots).

**Retire & lock:**

* After cutover, freeze old paths, archive code, and delete schedules. Track “retirement %” as a program KPI.

---

# D) 2026 month-by-month roadmap (who does what, when)

**M1–M2: Discover & design**

* Complete inventory & duplication map; agree the **semantic model** (dimensions, hierarchies, measures).
* Stand up base metadata tables & authoring workflow.
* Pick **two reference measures** for pilots: **(1) Exposure rollups**, **(2) Limit breach checks**.

**M3: Reference implementation (pilot)**

* Build registry-driven jobs in Snowflake; implement 10–15 core derivations as shared functions.
* Wire **Tasks** for EOD; basic run ledger; 10 critical DQ checks (incl. exposure↔limit reconciliation). ([docs.snowflake.com][1])

**M4: Parity & performance hardening**

* Dual-run pilots vs DB2/Spark; fix deltas; tune joins; selectively add **Search Optimization** and trial **QAS** on the worst offenders. ([docs.snowflake.com][3])

**M5–M6: Expand scope**

* Add **inventory/treasury/collateral** measures; reach 60–70% of total EOD compute.
* Introduce **Dynamic Tables** where dependency refresh is clearer than hand-rolled DAGs; enable **event logging** for refresh/task outcomes for observability. ([docs.snowflake.com][5])

**M7: Consumer contracts & lineage**

* Publish versioned output contracts for regulatory and analytics consumers; plug lineage into your catalog; enforce schema-compatibility gates in CI.

**M8–M9: Broad migration & SLAs**

* Dual-run broad set; ≥30 DQ rules live; MTTR metricing; EOD P95 ≤ **55 min** heading to ≤ **45–50 min**.

**M10: Cutover wave 1 (high-value sets)**

* Switch exposures/limits/inventory to Snowflake producers after 4 green EODs; retire 30–40% of duplicates.

**M11: Cutover wave 2 (remaining aggregates)**

* Finish remaining measures; finalize documentation & runbooks; set deprecation dates for any stragglers.

**M12: Stabilize & decommission**

* Prove month-end/quarter-end; complete retirements; deliver regulator-ready evidence pack (DQ, lineage, SLA, parity summary).

---

## What your teams will actually build (minimum)

* **Metadata tables (examples)**

  * `DIMENSION_DEF(dim_id, name, levels, valid_from/to)`
  * `HIERARCHY_DEF(hier_id, dim_id, parent, child, rollup_fn)`
  * `MEASURE_DEF(measure_id, name, formula_ref, rounding, unit, materiality)`
  * `AGG_DEFINITION(agg_id, measure_id, grain, filters, output_contract_id)`
  * `DERIVATION_RULE(rule_id, name, code_ref, version)`
  * `DQ_RULE(rule_id, target, type, threshold, severity)` / `DQ_RESULT(run_id, rule_id, status, count)`
  * `RUN(run_id, started_at, ended_at, status)` / `RUN_STEP(run_id, step_id, rows_in/out)`

* **Execution patterns**

  * SQL compiled from registry + UDF/Snowpark library; **Tasks** schedule the EOD run; **Dynamic Tables** for chained refresh where beneficial. ([docs.snowflake.com][1])

* **Hotspot accelerators (surgical use only)**

  * **Search Optimization** on high-cardinality lookup columns; **QAS** for spiky T-close workloads. ([docs.snowflake.com][3])

---

## Program KPIs (reported monthly)

* **Runtime:** EOD P95 ≤ 50 min; P99 ≤ 60 min.
* **Parity:** ≥ 99.9% parity on top-10 measures; ≤ 0.1% material variance overall.
* **DQ:** ≥ 30 rules live; < 0.1% unresolved exceptions at T+1.
* **Duplication:** ≥ 50% of duplicate SPs/Spark paths retired.
* **Change velocity:** New aggregation onboard ≤ 2 days.
* **Consumer happiness:** ≥ 8/10 survey (schema stability, timeliness, documentation).

---

## Risks & mitigations (short list)

* **Hidden derivations in legacy code** → Deep inventory + parity harness; SMEs sign-off per measure.
* **Month-end spikes** → Elastic warehouses + QAS toggle for peak; pre-aggregations for repeated joins. ([docs.snowflake.com][6])
* **Over-use of premium features** → Governance: allowlist for Search Optimization/QAS; monthly cost review. ([docs.snowflake.com][7])
* **Org change management** → Contract versioning, deprecation policy, and freeze window around cutover.

---

## Resourcing (typical)

* **Core**: 1 product owner, 1 solution architect, 3–4 data engineers (Snowflake/Snowpark/SQL), 1 DQ/observability engineer, 1 QA.
* **Embedded SMEs**: 1–2 credit risk analysts for measures & test oracles.
* **Ops**: 1 SRE shared (pipelines, alerting, on-call).

---

### Why this is feasible

* All execution primitives are **native Snowflake** (Tasks, Dynamic Tables, Snowpark, Search Optimization/QAS) and GA or long-standing; no exotic dependencies. ([docs.snowflake.com][1])
* The plan keeps ingestion out-of-scope, uses dual-run for safety, and focuses on your heaviest/high-value risk measures first (exposures & limits).

If you want, I can turn this into a one-pager slide and a metadata table starter kit (DDL + sample rows) tailored to your party/LE/country/industry hierarchy.

[1]: https://docs.snowflake.com/en/user-guide/tasks-intro?utm_source=chatgpt.com "Introduction to tasks"
[2]: https://docs.snowflake.com/en/release-notes/2025/9_04?utm_source=chatgpt.com "9.4 Release notes: Feb 24, 2025-Mar 01, 2025"
[3]: https://docs.snowflake.com/en/user-guide/search-optimization-service?utm_source=chatgpt.com "Search Optimization Service"
[4]: https://docs.snowflake.com/en/developer-guide/snowpark/index?utm_source=chatgpt.com "Snowpark API"
[5]: https://docs.snowflake.com/en/user-guide/dynamic-tables-about?utm_source=chatgpt.com "Dynamic tables"
[6]: https://docs.snowflake.com/en/user-guide/query-acceleration-service?utm_source=chatgpt.com "Using the Query Acceleration Service (QAS)"
[7]: https://docs.snowflake.com/en/user-guide/search-optimization/cost-estimation?utm_source=chatgpt.com "Search optimization cost estimation and management"
