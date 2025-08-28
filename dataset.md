Perfect 👍 Let’s sketch a conceptual metadata schema that is both generic (reusable across domains) and specialized enough to cover Credit Risk Workspace needs.


---

🏗️ Conceptual Metadata Schema (ERD-style in text form)

+------------------+
|   Dataset        |<-------------------------------+
+------------------+                                |
| dataset_id (PK)  |                                |
| name             |                                |
| description      |                                |
| domain           |   (e.g., Credit Risk, Market)  |
| version          |                                |
| effective_from   |                                |
| effective_to     |                                |
| owner_id (FK)    |                                |
+------------------+                                |
         |                                          |
         | 1..*                                     |
         v                                          |
+------------------+        +------------------+    |
|   Field          |        |   BusinessTerm   |    |
+------------------+        +------------------+    |
| field_id (PK)    |        | term_id (PK)     |    |
| dataset_id (FK)  |        | name             |    |
| name             |        | definition       |    |
| type             |        | regulatory_tag   |    |
| nullable_flag    |        | glossary_ref     |    |
| semantic_ref (FK)|------->| ...              |    |
+------------------+        +------------------+    |
         |                                          |
         | 0..*                                     |
         v                                          |
+------------------+                                |
|   QualityRule    |                                |
+------------------+                                |
| rule_id (PK)     |                                |
| field_id (FK)    |                                |
| type             |   (null check, range, etc.)    |
| threshold        |                                |
| severity         |   (warning/blocking)           |
| last_run_status  |                                |
+------------------+                                |
                                                    |
+------------------+                                |
|   Lineage        |                                |
+------------------+                                |
| lineage_id (PK)  |                                |
| dataset_id (FK)  |                                |
| source_system    |   (e.g., LoanSys, TradeSys)    |
| transformation   |   (rule/ETL step)              |
| downstream_sys   |   (Risk Aggregator, Dashboard) |
| last_updated     |                                |
+------------------+                                |
                                                    |
+------------------+                                |
|   Workflow       |                                |
+------------------+                                |
| workflow_id (PK) |                                |
| dataset_id (FK)  |                                |
| state            |   (draft, pending, approved)   |
| approver         |                                |
| approval_date    |                                |
| sla              |                                |
+------------------+                                |
                                                    |
+------------------+                                |
|   AccessControl  |                                |
+------------------+                                |
| access_id (PK)   |                                |
| dataset_id (FK)  |                                |
| role             |   (analyst, regulator, dev)    |
| permission       |   (read, write, approve)       |
+------------------+                                |
                                                    |
+------------------+                                |
|   AuditLog       |                                |
+------------------+                                |
| audit_id (PK)    |                                |
| dataset_id (FK)  |                                |
| user_id          |                                |
| action           |   (viewed, updated, approved)  |
| timestamp        |                                |
+------------------+                                |


---

🔑 How this works for Credit Risk

Dataset: Exposure, Limit, Trade, CounterpartyHierarchy

Field: exposure_usd, limit_usd, counterparty_id, is_primary

BusinessTerm: “Credit Exposure”, “Credit Limit”, “Ultimate Parent” (linked to Basel, CCAR definitions)

Lineage: Exposure dataset → Spark aggregation → Limit comparison engine → Regulatory report

QualityRule: exposure_usd >= 0, counterparty_id must exist in reference dataset

Workflow: Limit change approval (pending → approved by Risk Officer)

AccessControl: Analysts can view exposures, but only Risk Management can approve limits

AuditLog: Every limit change request and approval is logged



---

⚡ Extensibility

New domains (Market Risk, Liquidity Risk) just add new datasets & terms.

Scenario/Stress Test datasets can be modeled with versioning + lineage.

Regulatory mapping (Basel, FR Y-14) lives in BusinessTerm.regulatory_tag.



---

👉 Do you want me to draw this ERD in a diagram (visual) so you can see relationships more clearly, or keep it textual for now?

