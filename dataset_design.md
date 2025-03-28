# Design Document: Dataset Management API

## 1. Overview
This document outlines the implementation details of the Dataset Management API using Java Spring Boot and a DB2 database. The system provides endpoints to define, retrieve, and manage dataset metadata and supports audit tracking, entitlement control, and dataset storage location management.

## 2. Architecture
The solution consists of the following components:
- **Spring Boot REST API** for dataset management
- **DB2 Database** for metadata storage
- **Shell Script** for dataset operations
- **Entitlement Control** for access management
- **Audit Tracking** for logging actions

## 3. Data Model
### 3.1. Dataset Table
```sql
CREATE TABLE dataset (
    dataset_id VARCHAR(50) PRIMARY KEY,
    schema_json CLOB NOT NULL,
    dataset_keys VARCHAR(255),
    file_location VARCHAR(255),
    version INT DEFAULT 1,
    effective_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_at TIMESTAMP
);
```

### 3.2. Audit Table
```sql
CREATE TABLE audit_log (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset_id VARCHAR(50),
    action VARCHAR(50),
    performed_by VARCHAR(50),
    performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 4. RESTful Endpoints

### 4.1. Create Dataset Definition
**Endpoint:** `POST /run-mgmt/v1/dataset/definition`
**Request Body:**
```json
{
    "datasetId": "dataset_001",
    "schemaJson": "{...}",
    "datasetKeys": "key1,key2",
    "fileLocation": "${GPFS_LOCATION}/${cobDateKey}/${resource}/${datasetId}",
    "createdBy": "admin_user"
}
```
**Response:** `201 Created`

### 4.2. Retrieve Dataset Definition
**Endpoint:** `GET /run-mgmt/v1/dataset/definition/{datasetId}`
**Response:**
```json
{
    "datasetId": "dataset_001",
    "schemaJson": "{...}",
    "datasetKeys": "key1,key2",
    "fileLocation": "path/to/dataset",
    "version": 1,
    "effectiveDate": "2024-01-01T12:00:00Z"
}
```

### 4.3. Update Dataset Definition
**Endpoint:** `PUT /run-mgmt/v1/dataset/definition/{datasetId}`
**Request Body:** Similar to the create request.
**Response:** `200 OK`

### 4.4. Delete Dataset Definition
**Endpoint:** `DELETE /run-mgmt/v1/dataset/definition/{datasetId}`
**Response:** `204 No Content`

## 5. Dataset Versioning
- Each dataset update increments the `version` field automatically.
- Previous versions are retained in an archive table for rollback purposes.
- Implement an `is_latest` flag to indicate the most current version.
- Use versioning in retrieval to allow fetching historical dataset definitions.
- Example versioning query:
```sql
SELECT * FROM dataset WHERE dataset_id = 'dataset_001' ORDER BY version DESC FETCH FIRST 1 ROW ONLY;
```

## 6. Shell Script for CMD Operations
```sh
#!/bin/bash

# Usage: dataset_script.sh <operation> <dataset_id> <optional_payload_file>

OPERATION=$1
DATASET_ID=$2
PAYLOAD_FILE=$3
API_URL="http://localhost:8080/run-mgmt/v1/dataset/definition/$DATASET_ID"

if [ -z "$OPERATION" ] || [ -z "$DATASET_ID" ]; then
  echo "Usage: dataset_script.sh <operation> <dataset_id> <optional_payload_file>"
  exit 1
fi

case $OPERATION in
  GET)
    echo "Fetching dataset definition for $DATASET_ID..."
    curl -X GET "$API_URL" -H "Content-Type: application/json"
    ;;
  CREATE)
    if [ -z "$PAYLOAD_FILE" ]; then
      echo "Payload file required for CREATE operation."
      exit 1
    fi
    echo "Creating dataset definition for $DATASET_ID..."
    curl -X POST "$API_URL" -H "Content-Type: application/json" --data "@$PAYLOAD_FILE"
    ;;
  UPDATE)
    if [ -z "$PAYLOAD_FILE" ]; then
      echo "Payload file required for UPDATE operation."
      exit 1
    fi
    echo "Updating dataset definition for $DATASET_ID..."
    curl -X PUT "$API_URL" -H "Content-Type: application/json" --data "@$PAYLOAD_FILE"
    ;;
  DELETE)
    echo "Deleting dataset definition for $DATASET_ID..."
    curl -X DELETE "$API_URL" -H "Content-Type: application/json"
    ;;
  *)
    echo "Invalid operation. Supported operations: GET, CREATE, UPDATE, DELETE"
    exit 1
    ;;
esac
```

### 6.1 Validation and Misoperation Prevention
- Ensure dataset ID is not empty or malformed.
- Require a payload file for CREATE and UPDATE operations.
- Validate JSON structure before sending API requests.
- Prevent accidental DELETE operations by requiring a confirmation flag.
- Log all shell script executions for audit tracking.

## 7. Entitlement Control
- Use **Spring Security** to enforce user roles and permissions.
- Define roles: `DATASET_ADMIN`, `DATASET_USER`.
- Restrict dataset creation/modification to `DATASET_ADMIN`.
- Apply role-based access control (RBAC) on endpoints.

## 8. Audit Tracking
- Each action (CREATE, UPDATE, DELETE) triggers an insert into `audit_log`.
- Spring Boot **@TransactionalEventListener** captures actions and logs them.
- Track all shell script executions and API calls for security and compliance.

## 9. Conclusion
This implementation ensures a robust dataset management API with metadata storage, security, and audit tracking. Future enhancements can include extended dataset versioning with rollback functionality and more granular access controls.

