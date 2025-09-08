Nice — I’ll propose a production-ready, DB2-friendly data model for the Central Catalog + Lineage that’s designed to support a hybrid (process-first → dataset-enabled → selective data-driven) rollout. I’ll give:

1. a short conceptual model (entities + relationships),


2. practical DB schema (DDL-ready) for the core tables,


3. sample API / event contract (OpenLineage-style) and how events map to the model,


4. key queries you’ll need (impact analysis, latest runs, SLA check),


5. operational notes (indexes, partitioning, retention, governance), and


6. an incremental onboarding plan.




---

1) Conceptual model (brief)

Core entities:

Dataset: logical dataset (table, parquet path, CSV, API feed).

Dataset_Version / Artifact: physical instance produced at a time (path + row_count + checksum).

Process: job or workflow (Autosys job, Run Management request, Spark job).

Process_Run: an execution attempt of a Process.

Schema: column-level schema for a dataset version.

Lineage_Edge: graph edges connecting Processes ↔ Datasets (e.g., Process PRODUCES Dataset, Process CONSUMES Dataset).

Run_Event: raw emitted event from adapters (OpenLineage message).

SLA: SLA definitions attached to a Dataset or Process.

Quality_Check_Def & Result: DQ rule definitions and per-run results.

Steward / Owner / Team: governance info.


Relationships:

Process produces Dataset_Version.

Process consumes Dataset_Version (or Dataset).

Lineage is a directed graph built from these Process ↔ Dataset edges.

Control plane queries the graph to compute downstream/upstream impact and to decide triggers.



---

2) Core DB2 schema (DDL) — pragmatic, normalized, with JSON columns for extensibility

Below are the principal tables you’ll want to create first. I use DB2-standard types (VARCHAR, TIMESTAMP, CLOB) and IDENTITY surrogate keys for performance and stability.

> Note: adjust VARCHAR lengths to your standards and add SCHEMA.NAME prefix as needed.



-- 1. Dataset registry
CREATE TABLE DATASET (
  DATASET_ID       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  NAMESPACE        VARCHAR(200) NOT NULL,   -- e.g., 'db2.schema' or 's3://bucket/path'
  NAME             VARCHAR(300) NOT NULL,   -- logical name
  FULL_NAME        VARCHAR(600) NOT NULL,   -- NAMESPACE || '/' || NAME
  DESCRIPTION      CLOB,
  DEFAULT_FORMAT   VARCHAR(50),             -- 'DB2', 'PARQUET', 'CSV', 'API'
  OWNER_TEAM       VARCHAR(200),
  OWNER_EMAIL      VARCHAR(200),
  DEFAULT_SLA_ID   BIGINT,                  -- FK to SLA (nullable)
  STATUS           VARCHAR(20) DEFAULT 'DRAFT', -- DRAFT / ACTIVE / DEPRECATED
  CREATED_BY       VARCHAR(100),
  CREATED_AT       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UPDATED_AT       TIMESTAMP
);
CREATE UNIQUE INDEX IDX_DATASET_FULLNAME ON DATASET(FULL_NAME);

-- 2. Dataset version / artifact (the actual produced file/table instance)
CREATE TABLE DATASET_VERSION (
  DATASET_VERSION_ID BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  DATASET_ID         BIGINT NOT NULL REFERENCES DATASET(DATASET_ID),
  VERSION_TAG        VARCHAR(100),          -- e.g., 'v2025-09-01-0802' or UUID
  PATH               VARCHAR(1000),         -- physical path OR table name
  ROW_COUNT          BIGINT,
  CHECKSUM           VARCHAR(200),
  SCHEMA_HASH        VARCHAR(200),          -- to detect schema change
  PRODUCED_BY_RUN_ID CHAR(36),              -- maps to PROCESS_RUN.RUN_UUID
  PRODUCED_AT        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  METADATA_JSON      CLOB,                  -- optional extended facets (perf stats, etc)
  CONSTRAINT UNQ_DS_VER UNIQUE (DATASET_ID, VERSION_TAG)
);

-- 3. Column / schema store
CREATE TABLE DATASET_SCHEMA (
  SCHEMA_ID          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  DATASET_VERSION_ID BIGINT NOT NULL REFERENCES DATASET_VERSION(DATASET_VERSION_ID),
  COLUMN_ORDER       INTEGER,
  COLUMN_NAME        VARCHAR(200),
  COLUMN_TYPE        VARCHAR(200),
  NULLABLE           CHAR(1) DEFAULT 'Y',
  COLUMN_METADATA    CLOB
);

-- 4. Process registry (jobs / workflows)
CREATE TABLE PROCESS (
  PROCESS_ID      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  PROCESS_KEY     VARCHAR(300) NOT NULL,   -- e.g., 'AUTOSYS/BOXNAME/JobA' or 'RunMgmt/RequestType'
  NAME            VARCHAR(300),
  DESCRIPTION     CLOB,
  TECHNOLOGY      VARCHAR(50),              -- AUTOSYS, RUNMGR, SPARK, DB2_SP, SHELL
  TEAM            VARCHAR(200),
  OWNER_EMAIL     VARCHAR(200),
  TRIGGER_MODE    VARCHAR(20) DEFAULT 'PROCESS', -- PROCESS / DATASET / HYBRID
  CREATED_AT      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UPDATED_AT      TIMESTAMP
);
CREATE UNIQUE INDEX IDX_PROCESS_KEY ON PROCESS(PROCESS_KEY);

-- 5. Process run (execution record)
CREATE TABLE PROCESS_RUN (
  RUN_ID          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  RUN_UUID        CHAR(36) UNIQUE,          -- id from adapters / OpenLineage runId
  PROCESS_ID      BIGINT NOT NULL REFERENCES PROCESS(PROCESS_ID),
  START_TS        TIMESTAMP,
  END_TS          TIMESTAMP,
  STATUS          VARCHAR(20),              -- STARTED, SUCCESS, FAILED, CANCELLED
  ATTEMPT         INTEGER DEFAULT 1,
  PARENT_RUN_UUID CHAR(36),                 -- for nested flows or retries
  LOG_LOCATION    VARCHAR(1000),
  FACETS_JSON     CLOB,                     -- raw facets/payload
  CREATED_AT      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IDX_PROCESS_RUN_PROCESS_TS ON PROCESS_RUN(PROCESS_ID, START_TS);

-- 6. Lineage edges (graph)
-- Generic graph table: source_node -> target_node
-- node_type: 'PROCESS' or 'DATASET'
CREATE TABLE LINEAGE_EDGE (
  EDGE_ID         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  SOURCE_TYPE     VARCHAR(20) NOT NULL,  -- 'PROCESS' or 'DATASET'
  SOURCE_ID       BIGINT NOT NULL,
  TARGET_TYPE     VARCHAR(20) NOT NULL,  -- 'PROCESS' or 'DATASET'
  TARGET_ID       BIGINT NOT NULL,
  EDGE_ROLE       VARCHAR(30) NOT NULL,  -- e.g., 'PRODUCES', 'CONSUMES', 'DERIVES'
  TRANSFORM_META  CLOB,                  -- SQL snippet / transformation description
  CREATED_AT      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IDX_LINEAGE_SRC ON LINEAGE_EDGE (SOURCE_TYPE, SOURCE_ID);
CREATE INDEX IDX_LINEAGE_TGT ON LINEAGE_EDGE (TARGET_TYPE, TARGET_ID);

-- 7. Run events (raw adapter payloads)
CREATE TABLE RUN_EVENT (
  EVENT_ID        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  RUN_UUID        CHAR(36),
  PROCESS_KEY     VARCHAR(300),
  EVENT_TYPE      VARCHAR(50),         -- run.start, run.complete, dataset.register
  PAYLOAD_JSON    CLOB,
  RECEIVED_AT     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IDX_EVENT_RUNUUID ON RUN_EVENT(RUN_UUID);

-- 8. Dataset <-> SLA mapping
CREATE TABLE SLA (
  SLA_ID          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  NAME            VARCHAR(200),
  DESCRIPTION     CLOB,
  SLA_WINDOW_SEC  INTEGER,               -- allowed max time (e.g., freshness threshold)
  ESCALATION_JSON CLOB,
  CREATED_AT      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. Quality check definitions & results
CREATE TABLE QUALITY_CHECK_DEF (
  QDEF_ID         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  DATASET_ID      BIGINT REFERENCES DATASET(DATASET_ID),
  NAME            VARCHAR(200),
  SQL_EXPRESSION  CLOB,                  -- validation SQL or DSL
  EXPECTED_RESULT CLOB,                  -- e.g., 'count = 0'
  CREATED_AT      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE QUALITY_CHECK_RESULT (
  QRES_ID         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  QDEF_ID         BIGINT REFERENCES QUALITY_CHECK_DEF(QDEF_ID),
  DATASET_VERSION_ID BIGINT REFERENCES DATASET_VERSION(DATASET_VERSION_ID),
  EXECUTED_AT     TIMESTAMP,
  STATUS          VARCHAR(20),           -- PASS / FAIL / WARN
  DETAIL_JSON     CLOB
);

-- 10. Stewardship / approvals (basic)
CREATE TABLE DATASET_REGISTRATION (
  REG_ID          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  DATASET_ID      BIGINT REFERENCES DATASET(DATASET_ID),
  SUBMITTED_BY    VARCHAR(200),
  SUBMITTED_AT    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  STATUS          VARCHAR(20) DEFAULT 'PENDING', -- PENDING / APPROVED / REJECTED
  REVIEWER        VARCHAR(200),
  REVIEW_COMMENT  CLOB
);


---

3) Event / Adapter contract (OpenLineage-style mapping)

Use OpenLineage fields — map directly to RUN_EVENT, PROCESS_RUN, DATASET_VERSION, LINEAGE_EDGE.

Minimal adapter payload (JSON):

{
  "eventType": "START" | "COMPLETE",
  "run": {
    "runId": "uuid-1234",
    "startTime": "2025-09-08T08:02:00Z",
    "endTime": "2025-09-08T08:07:00Z",
    "facets": { "...": "..." }
  },
  "job": {
    "namespace": "AUTOSYS",
    "name": "BOX/JobA",
    "facets": {}
  },
  "inputs": [
    {"namespace":"DB2","name":"SCHEMA.TABLE_A","facets": {"version":"2025-09-08T07:30"}}
  ],
  "outputs": [
    {"namespace":"S3","name":"s3://bucket/credit-out/v20250908","facets":{"path":"s3://...","rows":12345}}
  ]
}

Mapping:

run.runId → PROCESS_RUN.RUN_UUID

job.namespace/name → PROCESS.PROCESS_KEY (create if missing)

inputs/outputs → create DATASET (if missing) and DATASET_VERSION entries; record LINEAGE_EDGE for PROCESS CONSUMES/PRODUCES DATASET


Adapter should POST this JSON to an ingestion API (POST /api/v1/run_event) which:

1. persists RUN_EVENT payload,


2. upserts PROCESS (by PROCESS_KEY),


3. inserts PROCESS_RUN,


4. upserts DATASET and DATASET_VERSION for each input/output,


5. inserts LINEAGE_EDGE rows for each relation, and


6. updates RUN_HISTORY / indexes.




---

4) Key queries & examples

A. Downstream impact (datasets/jobs impacted by dataset X) — recursive traversal:

WITH RECURSIVE DEPTH(SRC_TYPE, SRC_ID, TGT_TYPE, TGT_ID, DEPTH, PATH) AS (
  SELECT le.SOURCE_TYPE, le.SOURCE_ID, le.TARGET_TYPE, le.TARGET_ID, 1,
         CAST(le.SOURCE_TYPE || ':' || CHAR(le.SOURCE_ID) || '->' || le.TARGET_TYPE || ':' || CHAR(le.TARGET_ID) AS VARCHAR(2000))
  FROM LINEAGE_EDGE le
  WHERE le.SOURCE_TYPE = 'DATASET' AND le.SOURCE_ID = :dataset_id
  UNION ALL
  SELECT le.SOURCE_TYPE, le.SOURCE_ID, le.TARGET_TYPE, le.TARGET_ID, DEPTH+1,
         PATH || '->' || le.TARGET_TYPE || ':' || CHAR(le.TARGET_ID)
  FROM LINEAGE_EDGE le
  JOIN DEPTH d ON d.TGT_TYPE = le.SOURCE_TYPE AND d.TGT_ID = le.SOURCE_ID
  WHERE DEPTH < 12
)
SELECT * FROM DEPTH;

B. Last successful run for a dataset (assuming PRODUCED_BY_RUN_ID stored):

SELECT pr.*
FROM PROCESS_RUN pr
JOIN DATASET_VERSION dv ON dv.PRODUCED_BY_RUN_ID = pr.RUN_UUID
JOIN DATASET d ON d.DATASET_ID = dv.DATASET_ID
WHERE d.FULL_NAME = 'db2.schema/table_x'
  AND pr.STATUS = 'SUCCESS'
ORDER BY pr.END_TS DESC
FETCH FIRST 1 ROW ONLY;

C. Which processes depend on a dataset (direct consumers):

SELECT p.process_id, p.process_key
FROM LINEAGE_EDGE le
JOIN PROCESS p ON (le.TARGET_TYPE='PROCESS' AND le.TARGET_ID = p.process_id)
WHERE le.SOURCE_TYPE='DATASET' AND le.SOURCE_ID = :dataset_id;

D. SLA check: datasets past freshness threshold:

A scheduled job can find datasets whose latest version is older than SLA_WINDOW_SEC:


SELECT d.DATASET_ID, d.FULL_NAME, MAX(dv.PRODUCED_AT) AS LAST_PROD
FROM DATASET d
LEFT JOIN DATASET_VERSION dv ON dv.DATASET_ID = d.DATASET_ID
GROUP BY d.DATASET_ID, d.FULL_NAME
HAVING MAX(dv.PRODUCED_AT) < (CURRENT_TIMESTAMP - <SLA_WINDOW_SEC> SECONDS);


---

5) Operational & scale recommendations

Primary store: DB2 (your central canonical metadata). Keep small JSON facets in CLOB.

Graph queries: DB2 recursive CTEs are OK for typical traversals up to moderate depth. If you need very large graph traversals and complex analytics, consider syncing LINEAGE into a graph DB (Neo4j / JanusGraph) for interactive exploration — but keep DB2 as the canonical source of truth.

Indexes: index DATASET(FULL_NAME), PROCESS(PROCESS_KEY), PROCESS_RUN(RUN_UUID), LINEAGE_EDGE on source/target. Consider composite indexes for queries you run most (e.g., (DATASET_ID, PRODUCED_AT)).

Partitioning: partition PROCESS_RUN and RUN_EVENT by DATE(START_TS) for high-throughput scenarios and fast purges. Use table-partitioning policies in DB2.

Retention: hot run history (90 days) in primary DB; older runs archived to compressed storage or separate archive schema. Keep aggregated metrics long-term (daily/hourly rollups).

Idempotency: adapters should use RUN_UUID as global idempotent key; ingestion API must upsert using RUN_UUID.

Backfill: add a HISTORICAL flag to RUN_EVENT ingestion to support bulk historical ingestion with backfill mode.

Transactions: ingestion that inserts PROCESS_RUN + DATASET_VERSION + LINEAGE should be transactional to avoid partial state. Use compensating logic on failures.

Security & RBAC: add roles (PLATFORM_ADMIN, DATA_STEWARD, DATA_OWNER, DATA_CONSUMER). Restrict DDL operations to admins; dataset ownership edits to stewards/owners. Mask sensitive metadata if needed.

Governance workflow: support auto-registration + steward approval. Use DATASET_REGISTRATION table to store pending approvals.



---

6) How the model supports hybrid (process-first → data-driven)

Trigger mode on PROCESS (TRIGGER_MODE = PROCESS / DATASET / HYBRID) lets you keep existing Autosys jobs process-driven while allowing new/converted processes to be data-driven. The control plane checks TRIGGER_MODE before deciding whether to invoke by job-completion or dataset-ready event.

LINEAGE_EDGE stores both PRODUCES and CONSUMES relations so the graph can represent both JobA -> DatasetX -> JobB (hybrid) and JobA -> JobB (process-only) if you choose to create job-to-job edges from Autosys dependency metadata.

Dataset_Version records enable dataset-level SLA and freshness checks even if the producer is a legacy job.

Run_Event ingestion builds provenance automatically without changing process logic — adapters wrap the job call and emit events.



---

7) Incremental onboarding plan tied to this model

1. Phase 0 — Bootstrapping

Create the core tables above.

Implement ingestion API (POST /api/v1/run_event) to persist RUN_EVENT and upsert relevant PROCESS and DATASET records.



2. Phase 1 — Process-driven registry

Auto-scan Autosys configs and insert PROCESS rows (PROCESS_KEY populated).

Create LINEAGE edges for job-to-job dependencies (lightweight).



3. Phase 2 — Adapter pilot

Build adapter for Autosys job wrapper for 10–20 critical jobs that emits events. Confirm mapping to DATASET and DATASET_VERSION.

Populate PROCESS_RUN and dataset_version records; visualize lineage.



4. Phase 3 — SLA & Observability

Add SLA rows for top datasets, build dashboard.

Run quality checks on produced DATASET_VERSION and record results.



5. Phase 4 — Gradual scale

Batch import processes/datasets via heuristics and owner validation.

Add more adapters for Run Management and Spark.

Optionally sync LINEAGE into a graph DB for exploration.





---

8) API endpoints (minimum set)

POST /api/v1/run_event — ingest adapter events (OpenLineage-style).

GET  /api/v1/dataset/{id} — get dataset metadata + latest versions.

GET  /api/v1/dataset/{id}/lineage?direction=downstream&depth=5 — hierarchical lineage.

GET  /api/v1/process/{id} — process metadata + last runs.

GET  /api/v1/sla/status — SLA violations.

POST /api/v1/dataset/register — request registration (creates DATASET_REGISTRATION).



---

9) Example – lifecycle of one run

1. Adapter wraps Autosys job call, emits run.start (runId = uuid).


2. Adapter runs job, on completion parses outputs and emits run.complete with outputs[] (path, row_count, schema_facet).


3. Ingestion API:

persists RUN_EVENT,

upserts PROCESS by PROCESS_KEY,

inserts PROCESS_RUN (RUN_UUID),

upserts DATASET entries and inserts DATASET_VERSION rows,

inserts LINEAGE_EDGE rows for each PROCESS -> PRODUCES -> DATASET and PROCESS <- CONSUMES <- DATASET,

triggers policy engine for SLA and quality checks.





---

10) Final recommendations & next steps

Start with the core tables + ingestion API above and build a small Autosys adapter for 10–20 critical runs. This proves the model with minimal disruption.

Keep DB2 as canonical metadata store; optionally replicate lineage into a graph DB for UX-heavy exploration.

Instrument idempotent ingestion and backfill support. Plan storage/retention for RUN_EVENT and PROCESS_RUN.

Prepare stewarding UI for dataset approval and owner mapping.



---

If you want I can:

produce complete DB2 DDL for the entire model (I gave the core tables above — I can expand into FK constraints, privileges, materialized views),

create the exact ingestion pseudo-code (endpoint flow + SQL transactions) that maps OpenLineage events to these tables, or

draft the API swagger / JSON examples for the adapter so developers can implement wrappers quickly.


Which of those would be most useful next?

