```mermaid
flowchart LR
  Browser["React + TypeScript browser"]
  API["Spring Boot REST API"]
  SSE["Spring Boot SSE endpoint"]
  DB[("PostgreSQL")]
  BetterStack["Better Stack API"]
  OIDC["Google OIDC"]

  Browser -->|"OIDC login / session"| OIDC
  Browser -->|"REST reads and CSRF-protected writes"| API
  Browser <-->|"SSE event stream / Last-Event-ID"| SSE
  API --> DB
  SSE --> DB
  API -->|"server-side HTTPS, timeout, bounded retry, cache"| BetterStack
```

## Incident visibility and event flow

Authenticated viewers can submit reports. A new report and its `CREATED` event
are stored as unpublished; only operators can see the pending queue and
publish, edit, or archive reports. Public incident reads and streams include
published reports only. Archiving hides a report from active views but retains
the incident, audit entry, and event history. Audit actors come from the
validated OIDC identity. All writes use the REST API with a session CSRF token;
the browser's SSE connection is read-only.

```mermaid
erDiagram
  USERS o|--o{ INCIDENTS : reports
  USERS o|--o{ INCIDENTS : assigned_to
  INCIDENTS ||--o{ INCIDENT_EVENTS : emits
  USERS {
    bigint id PK
    string email UK
    string role
  }
  INCIDENTS {
    bigint id PK
    bigint reporter_id FK
    bigint assigned_to FK
    string title
    string status
    string severity
    boolean published
    boolean archived
    timestamptz created_at
    timestamptz updated_at
  }
  INCIDENT_EVENTS {
    bigint id PK
    bigint incident_id FK
    string event_type
    text payload_json
    boolean published
    boolean archived
    timestamptz created_at
  }
  AUDIT_LOGS {
    bigint id PK
    string actor_email
    string action
    string resource_type
    bigint resource_id
    text before_state_json
    text after_state_json
    timestamptz timestamp
  }
  INCIDENT_EVENT_STREAM_LOCK {
    int id PK
  }
```

`audit_logs.resource_id` is a logical resource reference rather than a foreign
key, so the audit trail can remain append-only independently of resource
lifecycle. The V4 index `idx_incidents_visibility_created_at` begins with the
publication/archive filters and orders by creation time, supporting the
public and operator active-incident queries without sorting the whole table.
V5 adds `idx_audit_logs_resource_history` to support a resource's audit timeline;
it can be dropped without changing stored audit records.