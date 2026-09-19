# Architecture

Claim Management System — healthcare professional claims.
Java 21 · Spring Boot 4.1.1 · PostgreSQL 17 · React 19 + TypeScript · Docker Compose

Every diagram below is Mermaid and renders on GitHub, in VS Code with the Mermaid extension, and at mermaid.live.

---

## 1. System context

Who and what the system talks to. Phase 2 integrations are drawn dashed because they are specified but not built.

```mermaid
flowchart TB
    biller["Medical biller<br/><i>creates and works claims</i>"]
    admin["Billing manager<br/><i>full access, reference data</i>"]
    auditor["Auditor<br/><i>read only</i>"]

    subgraph system["Claim Management System"]
        cms["Claims capture, workflow,<br/>audit trail and reporting"]
    end

    clearing["Clearinghouse<br/><i>837P out, 277 status back</i>"]
    payer["Payer<br/><i>835 remittance, 270/271 eligibility</i>"]

    biller   --> cms
    admin    --> cms
    auditor  --> cms

    cms -.->|"Phase 2.1 / 2.2"| clearing
    cms -.->|"Phase 2.3"| payer

    classDef phase2 stroke-dasharray: 5 4,stroke:#8a5a06,color:#8a5a06;
    class clearing,payer phase2
```

---

## 2. Container diagram

What actually runs, and how the pieces talk. All three containers sit on one Docker network; only the SPA and the API are published to the host.

```mermaid
flowchart LR
    browser["Browser<br/>React 19 SPA"]

    subgraph docker["Docker Compose network"]
        nginx["<b>cms-frontend</b><br/>nginx 1.27<br/>serves the SPA,<br/>proxies /api<br/>:80 → host :9000"]
        api["<b>cms-backend</b><br/>Spring Boot 4.1.1 on JRE 21<br/>REST API + JWT<br/>:9090 → host :9090"]
        db[("<b>cms-db</b><br/>PostgreSQL 17<br/>Flyway-managed schema<br/>:5432 → host :54321")]
    end

    browser -->|"HTTP :9000"| nginx
    nginx   -->|"static assets"| browser
    nginx   -->|"proxy /api, /swagger-ui, /actuator"| api
    api     -->|"JDBC, HikariCP pool of 10"| db

    note["Single browser origin<br/>⇒ no CORS, no backend URL in the bundle"]
    nginx -.- note

    classDef n fill:#fdf1dc,stroke:#e0c48a,color:#6d470a;
    class note n
```

**Why nginx proxies the API rather than the SPA calling `:9090` directly:** the browser only ever sees one origin, which removes CORS configuration, keeps the backend hostname out of the JavaScript bundle, and means the same image works in any environment without a rebuild.

---

## 3. Component diagram — backend

Layering inside the Spring Boot application. Arrows point the way calls flow. Nothing skips a layer.

```mermaid
flowchart TB
    subgraph web["web · HTTP boundary"]
        authC["AuthController"]
        claimC["ClaimController"]
        refC["Patient / Payer /<br/>Provider / Policy<br/>Controllers"]
        lookupC["ReferenceController<br/><i>publishes the state machine</i>"]
        p2C["Phase2Controller<br/><i>501 + X-Phase: 2</i>"]
    end

    subgraph sec["security"]
        filter["OAuth2 resource server<br/>JWT filter"]
        cfg["SecurityConfig<br/>@PreAuthorize rules"]
        uds["AppUserDetailsService"]
    end

    subgraph svc["service · business rules"]
        authS["AuthService<br/>+ TokenService"]
        claimS["ClaimService"]
        machine["<b>ClaimStatusMachine</b><br/>the only place status changes"]
        refS["ReferenceDataService"]
        mapper["ClaimMapper"]
        specs["ClaimSpecifications"]
    end

    subgraph repo["repository · Spring Data JPA"]
        repos["ClaimRepository<br/>ClaimStatusHistoryRepository<br/>Patient / Payer / Provider /<br/>Policy / AppUser repositories"]
    end

    subgraph dom["domain · entities and enums"]
        ents["Claim, ClaimLine, ClaimDiagnosis,<br/>ClaimStatusHistory, Patient, Payer,<br/>Provider, InsurancePolicy, AppUser"]
        enum["<b>ClaimStatus</b><br/>states + legal transitions"]
    end

    err["GlobalExceptionHandler<br/>RFC 9457 problem+json"]
    dbx[("PostgreSQL 17")]

    filter --> web
    cfg --> filter
    uds --> repos

    authC --> authS
    claimC --> claimS
    refC --> refS
    lookupC --> enum
    p2C --> err

    claimS --> machine
    claimS --> specs
    claimS --> mapper
    claimS --> repos
    refS --> repos
    authS --> repos
    machine --> enum
    mapper --> ents
    repos --> ents
    repos --> dbx

    web -.->|"exceptions"| err

    classDef hot fill:#e8effc,stroke:#1f5fd6,color:#123a8a;
    classDef p2 fill:#fdf1dc,stroke:#e0c48a,color:#6d470a;
    class machine,enum hot
    class p2C p2
```

**The load-bearing idea:** `ClaimStatus` (an enum) owns the transition table, `ClaimStatusMachine` is the only code that changes a claim's status, and `GET /api/reference/status-transitions` serialises that same enum to the UI. Three layers, one source of truth, no possibility of the button set disagreeing with what the API will accept.

---

## 4. Entity relationship diagram

```mermaid
erDiagram
    PATIENT ||--o{ INSURANCE_POLICY : "is covered by"
    PAYER   ||--o{ INSURANCE_POLICY : "issues"
    PATIENT ||--o{ CLAIM            : "is subject of"
    PROVIDER||--o{ CLAIM            : "renders"
    PAYER   ||--o{ CLAIM            : "is billed"
    INSURANCE_POLICY ||--o{ CLAIM   : "pays under"
    CLAIM   ||--|{ CLAIM_LINE       : "bills"
    CLAIM   ||--|{ CLAIM_DIAGNOSIS  : "is coded with"
    CLAIM   ||--o{ CLAIM_STATUS_HISTORY : "audited by"

    APP_USER {
        bigint  id PK
        varchar username UK
        varchar password_hash "BCrypt cost 10"
        varchar full_name
        varchar role "ADMIN | BILLER | VIEWER"
        boolean enabled
    }

    PATIENT {
        bigint  id PK
        varchar mrn UK "medical record number"
        varchar first_name
        varchar last_name
        date    date_of_birth
        varchar phone
        varchar email
        varchar address_line1
        varchar city
        varchar state
        varchar postal_code
    }

    PAYER {
        bigint  id PK
        varchar payer_code UK
        varchar name
        varchar plan_type "COMMERCIAL | MEDICARE | MEDICAID | ..."
        varchar claims_address
        boolean active
    }

    PROVIDER {
        bigint  id PK
        varchar npi UK "10 digits, CHECK constrained"
        varchar first_name
        varchar last_name
        varchar specialty
        varchar tax_id
        boolean active
    }

    INSURANCE_POLICY {
        bigint  id PK
        bigint  patient_id FK
        bigint  payer_id FK
        varchar member_id
        varchar group_number
        varchar priority "PRIMARY | SECONDARY | TERTIARY"
        date    effective_date
        date    termination_date "null = open ended"
    }

    CLAIM {
        bigint  id PK
        varchar claim_number UK "CLM-YYYY-NNNNNN"
        bigint  patient_id FK
        bigint  provider_id FK
        bigint  payer_id FK
        bigint  policy_id FK "nullable"
        varchar status "CHECK constrained"
        date    service_date_from
        date    service_date_to
        varchar place_of_service "CMS 2-digit code"
        numeric total_charge "12,2 - always SUM of lines"
        numeric allowed_amount "12,2"
        numeric paid_amount "12,2"
        numeric patient_responsibility "12,2"
        varchar notes
        timestamptz submitted_at
        varchar created_by
        bigint  version "optimistic lock"
    }

    CLAIM_LINE {
        bigint  id PK
        bigint  claim_id FK
        int     line_number "UK with claim_id"
        varchar cpt_code "CPT/HCPCS, regex checked"
        varchar modifiers
        date    service_date
        int     units "CHECK > 0"
        numeric charge_amount "12,2"
        numeric allowed_amount "12,2"
        numeric paid_amount "12,2"
        varchar description
    }

    CLAIM_DIAGNOSIS {
        bigint  id PK
        bigint  claim_id FK
        varchar icd10_code "ICD-10-CM, regex checked"
        varchar description
        int     sequence_no "1..12, 1 = principal"
    }

    CLAIM_STATUS_HISTORY {
        bigint  id PK
        bigint  claim_id FK
        varchar from_status "null on creation"
        varchar to_status
        varchar reason
        varchar changed_by
        timestamptz changed_at
    }
```

### Schema decisions worth defending

| Decision | Reason |
|---|---|
| `NUMERIC(12,2)` for every money column | Floating point in a billing system produces reconciliation defects that surface months later. |
| `CHECK` constraints on status, CPT, ICD-10, NPI, units, dates | The database refuses bad data even if a future service bypasses the API. Defence in depth. |
| `claim_status_history` is append-only | The audit trail is the compliance artefact. Nothing in the codebase issues `UPDATE` or `DELETE` against it. |
| `@Version` on `claim` | Two billers editing the same draft is routine. The second save gets a 409, not a silent overwrite. |
| Partial index `WHERE status NOT IN ('PAID','VOID')` | Open AR is the hot query. Indexing only open rows keeps it small as history grows. |
| Flyway with `ddl-auto: validate` | Schema is versioned and reviewable. Hibernate never alters a claims table. |

---

## 5. Claim status state machine

The workflow, exactly as `ClaimStatus` encodes it.

```mermaid
stateDiagram-v2
    [*] --> DRAFT : create

    DRAFT --> SUBMITTED : submit<br/><i>needs at least one line,<br/>one diagnosis, active coverage,<br/>and a non-zero charge</i>
    DRAFT --> VOID : void<br/><i>reason required</i>

    SUBMITTED --> ACCEPTED : payer acknowledges
    SUBMITTED --> REJECTED : rejected pre-adjudication<br/><i>reason required</i>
    SUBMITTED --> VOID : void

    REJECTED --> DRAFT : correct and refile<br/><i>clears submitted_at and amounts</i>
    REJECTED --> VOID : void

    ACCEPTED --> PAID : post payment in full
    ACCEPTED --> PARTIALLY_PAID : post partial payment
    ACCEPTED --> DENIED : denied<br/><i>reason required</i>
    ACCEPTED --> VOID : void

    PARTIALLY_PAID --> PAID : post balance
    PARTIALLY_PAID --> DENIED : remainder denied
    PARTIALLY_PAID --> VOID : void

    DENIED --> VOID : void
    DENIED --> APPEALED : appeal 🚧 Phase 2<br/><i>returns 501 today</i>

    APPEALED --> PAID : appeal upheld
    APPEALED --> DENIED : appeal lost
    APPEALED --> VOID : void

    PAID --> [*]
    VOID --> [*]

    note right of DRAFT
        Only DRAFT is editable.
        Every other status rejects
        content changes with 409.
    end note

    note right of PAID
        PAID and VOID are terminal.
        Nothing follows them.
    end note
```

### Guard rules enforced in `ClaimStatusMachine`

| Transition | Guard | Side effect |
|---|---|---|
| `→ SUBMITTED` | ≥1 line, ≥1 diagnosis, total charge > 0, policy active on the service date | sets `submitted_at` |
| `→ REJECTED` `→ DENIED` `→ VOID` | reason is mandatory | — |
| `REJECTED → DRAFT` | — | clears `submitted_at`, allowed, paid and patient responsibility |
| `→ PAID` | `paidAmount` required and ≤ allowed | sets allowed, paid, and patient responsibility = allowed − paid |
| `→ PARTIALLY_PAID` | `paidAmount` > 0 and strictly < allowed | same, balance stays outstanding |
| `DENIED → APPEALED` | blocked in Phase 1 | returns 501 with a pointer to the spec |
| anything else | not in the transition table | 409 listing what *is* allowed from here |

---

## 6. Sequence — authentication

```mermaid
sequenceDiagram
    autonumber
    actor U as Biller
    participant SPA as React SPA
    participant NG as nginx
    participant API as AuthController
    participant AS as AuthService
    participant TS as TokenService
    participant DB as PostgreSQL

    U->>SPA: username + password
    SPA->>NG: POST /api/auth/login
    NG->>API: proxy
    API->>AS: login(request)
    AS->>DB: SELECT * FROM app_user WHERE username = ?
    DB-->>AS: row or empty

    alt no such user OR wrong password
        AS-->>API: BadCredentialsException
        Note over AS,API: Identical error either way,<br/>so the response never<br/>confirms which usernames exist
        API-->>SPA: 401 problem+json
    else credentials valid
        AS->>AS: BCrypt.matches(raw, hash)
        AS->>TS: issue(user)
        TS->>TS: sign HS256 JWT<br/>sub, roles[], name, exp
        TS-->>AS: token + expiry
        AS-->>API: LoginResponse
        API-->>SPA: 200 { token, role, expiresAt }
        SPA->>SPA: store token in localStorage
    end

    Note over SPA,API: Every later call sends<br/>Authorization: Bearer <token>.<br/>A 401 clears the token and<br/>bounces to /login.
```

---

## 7. Sequence — create a claim

```mermaid
sequenceDiagram
    autonumber
    actor B as Biller
    participant SPA as ClaimFormPage
    participant API as ClaimController
    participant CS as ClaimService
    participant R as Repositories
    participant DB as PostgreSQL

    B->>SPA: fill encounter, diagnoses, service lines
    SPA->>SPA: live total = Σ line charges (display only)
    SPA->>API: POST /api/claims

    API->>API: @Valid — CPT, ICD-10, units, dates, NPI
    alt validation fails
        API-->>SPA: 400 problem+json with field-level errors
    end

    API->>API: @PreAuthorize hasAnyRole ADMIN, BILLER
    alt VIEWER
        API-->>SPA: 403 problem+json
    end

    API->>CS: create(request)
    CS->>CS: validateDates — line dates inside the claim period
    CS->>R: load patient, provider, payer, policy
    R->>DB: SELECT
    CS->>CS: policy must belong to the patient
    CS->>CS: mint CLM-YYYY-NNNNNN
    CS->>CS: <b>totalCharge = Σ line charges</b><br/>never taken from the client
    CS->>R: save(claim) with lines + diagnoses cascaded
    CS->>R: save(history: null → DRAFT)
    R->>DB: INSERT claim, claim_line, claim_diagnosis, claim_status_history
    DB-->>R: ok
    CS-->>API: ClaimResponse
    API-->>SPA: 201 Created + Location header
    SPA->>SPA: navigate to /claims/{id}
```

---

## 8. Sequence — submit a claim, and what happens when it cannot be submitted

```mermaid
sequenceDiagram
    autonumber
    actor B as Biller
    participant SPA as ClaimDetailPage
    participant API as ClaimController
    participant CS as ClaimService
    participant SM as ClaimStatusMachine
    participant DB as PostgreSQL

    Note over SPA: Buttons come from<br/>claim.allowedTransitions,<br/>which the server computed<br/>from the enum

    B->>SPA: click "SUBMITTED"
    SPA->>API: POST /api/claims/{id}/transition { targetStatus: SUBMITTED }
    API->>CS: transition(id, request)
    CS->>DB: load claim with references
    CS->>SM: apply(claim, request, actor)

    SM->>SM: is SUBMITTED reachable from DRAFT?
    alt illegal transition
        SM-->>API: ConflictException
        API-->>SPA: 409 "Cannot move CLM-… from PAID to SUBMITTED.<br/>Allowed from PAID: none — PAID is a terminal state."
    else legal
        SM->>SM: guard - at least one line, one diagnosis, non-zero charge
        SM->>SM: guard - policy active on service_date_from
        alt guard fails
            SM-->>API: BusinessRuleException
            API-->>SPA: 400 "Policy AET7781140 is not active on the<br/>service date 2026-08-26."
        else guards pass
            SM->>SM: set submitted_at = now
            SM->>SM: status = SUBMITTED
            SM-->>CS: ClaimStatusHistory row
            CS->>DB: UPDATE claim, INSERT claim_status_history
            CS-->>API: ClaimResponse with new allowedTransitions
            API-->>SPA: 200
            SPA->>SPA: re-render badge, buttons and timeline
        end
    end
```

---

## 9. Sequence — posting a payment

```mermaid
sequenceDiagram
    autonumber
    actor B as Biller
    participant SPA as Transition modal
    participant SM as ClaimStatusMachine
    participant DB as PostgreSQL

    B->>SPA: choose PAID, enter allowed 246.40, paid 197.12
    SPA->>SM: POST transition { PAID, allowedAmount, paidAmount }

    SM->>SM: allowed = request value, else claim value, else total charge
    alt paid exceeds allowed
        SM-->>SPA: 400 "Paid amount 300.00 exceeds the allowed amount 246.40"
    else ok
        SM->>SM: claim.allowedAmount = 246.40
        SM->>SM: claim.paidAmount = 197.12
        SM->>SM: claim.patientResponsibility = allowed − paid = 49.28
        SM->>SM: status = PAID
        SM->>DB: UPDATE claim + INSERT history
        SM-->>SPA: 200, outstanding recomputed to 49.28
    end

    Note over SM: PARTIALLY_PAID takes the same path<br/>but requires a payment above zero and<br/>below the allowed amount, so the<br/>balance stays in AR.
```

---

## 10. Sequence — a Phase 2 endpoint

The handoff is visible at runtime, not only in a document.

```mermaid
sequenceDiagram
    autonumber
    actor B as Biller
    participant SPA as RoadmapPage
    participant P2 as Phase2Controller
    participant EH as GlobalExceptionHandler

    B->>SPA: click "Probe endpoint"
    SPA->>P2: POST /api/edi/837p/export
    P2->>P2: @PreAuthorize passes — the route is real
    P2->>EH: throw NotImplementedYetException("EDI 837P claim export", "2.1")
    EH-->>SPA: 501 Not Implemented<br/>X-Phase: 2<br/>{ detail, phase: 2, handoffRef: "2.1",<br/>specification: "docs/PHASE2_HANDOFF.md#2.1" }
    SPA->>B: shows the exact response

    Note over P2,EH: 501 with a spec pointer distinguishes<br/>"not built yet" from "broken",<br/>which is the whole point of the boundary.
```

---

## 11. Request flow through the stack

```mermaid
flowchart LR
    A["Browser<br/>fetch /api/claims"] --> B["nginx<br/>location /api/"]
    B --> C["Spring Security<br/>JWT filter chain"]
    C -->|"no / bad token"| C1["401 problem+json"]
    C --> D["@PreAuthorize<br/>role check"]
    D -->|"wrong role"| D1["403 problem+json"]
    D --> E["Controller<br/>@Valid bean validation"]
    E -->|"invalid"| E1["400 + field errors"]
    E --> F["Service<br/>business rules"]
    F -->|"rule broken"| F1["400 problem+json"]
    F -->|"illegal transition<br/>or non-DRAFT edit"| F2["409 problem+json"]
    F --> G["Repository<br/>Spring Data JPA"]
    G --> H[("PostgreSQL<br/>CHECK constraints")]
    H -->|"constraint violation"| H1["409 problem+json"]
    H --> I["200 / 201 JSON"]

    classDef err fill:#fdeaea,stroke:#d99,color:#7f1717;
    classDef ok  fill:#e2f4ee,stroke:#9c9,color:#0d5c46;
    class C1,D1,E1,F1,F2,H1 err
    class I ok
```

Five independent layers can reject a request, and each returns the same RFC 9457 `problem+json` shape. The client handles one error format.

---

## 12. Deployment topology

```mermaid
flowchart TB
    subgraph host["Developer machine or single VM"]
        subgraph net["docker network: default bridge"]
            fe["cms-frontend<br/>nginx:1.27-alpine<br/>~25 MB"]
            be["cms-backend<br/>eclipse-temurin:21-jre-alpine<br/>non-root user 'app'<br/>-XX:MaxRAMPercentage=75"]
            db[("cms-db<br/>postgres:17-alpine<br/>volume: db_data")]
        end
        p9000["host :9000"] --- fe
        p9090["host :9090"] --- be
        p54321["host :54321"] --- db
    end

    fe -->|"http://backend:9090"| be
    be -->|"jdbc:postgresql://db:5432/claims"| db

    fe -.->|"healthcheck: GET /"| fe
    be -.->|"healthcheck: /actuator/health"| be
    db -.->|"healthcheck: pg_isready"| db

    start["docker compose up --build"] --> host
```

**Startup ordering is enforced by health, not by sleep.** `backend` waits for `db` to report `service_healthy`; `frontend` waits for `backend`. Flyway runs its migrations on backend start, so the schema and the seed data exist before the first request.

---

## 13. Build pipeline inside the images

```mermaid
flowchart LR
    subgraph beb["backend/Dockerfile"]
        b1["maven:3.9-eclipse-temurin-21"] --> b2["COPY pom.xml<br/>dependency:go-offline<br/><i>cached layer</i>"]
        b2 --> b3["COPY src<br/>mvn package"]
        b3 --> b4["claim-management.jar"]
        b4 --> b5["eclipse-temurin:21-jre-alpine<br/><i>runtime only, no Maven</i>"]
    end

    subgraph feb["frontend/Dockerfile"]
        f1["node:22-alpine"] --> f2["npm ci<br/><i>cached layer</i>"]
        f2 --> f3["tsc -b && vite build"]
        f3 --> f4["dist/"]
        f4 --> f5["nginx:1.27-alpine<br/><i>runtime only, no Node</i>"]
    end
```

Multi-stage on both sides: the build toolchain never ships. The runtime images carry a JRE and a static web server, nothing else.

---

## 14. Phase roadmap

```mermaid
gantt
    title Delivery phases
    dateFormat YYYY-MM-DD
    axisFormat %b %d

    section Phase 1 — 80%, delivered
    Repo, Docker stack, Postgres        :done, p1a, 2026-09-15, 1d
    Schema + Flyway + seed data         :done, p1b, after p1a, 1d
    Domain, repositories, JWT security  :done, p1c, after p1b, 1d
    State machine + claim API           :done, p1d, after p1c, 1d
    React SPA + audit timeline          :done, p1e, after p1d, 1d
    Phase 2 stubs + docs                :done, p1f, after p1e, 1d

    section Phase 2 — 15%, handoff
    2.1 EDI 837P export                 :p2a, after p1f, 4d
    2.2 ERA 835 ingest                  :p2b, after p2a, 5d
    2.3 Eligibility 270/271             :p2c, after p1f, 3d
    2.4 Denials and appeals             :p2d, after p2b, 4d
    2.5 Attachments                     :p2e, after p1f, 2d
    2.6 Analytics and reporting         :p2f, after p2d, 3d

    section Phase 3 — 5%, hardening
    Tests and CI                        :p3a, after p2f, 3d
    Auth hardening, observability       :p3b, after p3a, 2d
    PHI audit log, secrets management   :p3c, after p3b, 2d
```

---

## 15. Where each rule lives

A single claim rule should exist in exactly one place. This is the map.

```mermaid
flowchart TB
    subgraph L1["Database — last line of defence"]
        d1["CHECK: status in the 9 valid values"]
        d2["CHECK: CPT and ICD-10 regex"]
        d3["CHECK: NPI is 10 digits, units above zero"]
        d4["UNIQUE: claim_number, mrn, npi, payer_code"]
        d5["FK integrity + ON DELETE CASCADE for lines"]
    end

    subgraph L2["Bean Validation — shape of the request"]
        v1["@NotNull, @Size, @Pattern, @DecimalMin"]
        v2["@NotEmpty on lines and diagnoses"]
        v3["Returns 400 with per-field messages"]
    end

    subgraph L3["Service — business rules"]
        s1["totalCharge = Σ line charges, always"]
        s2["line service dates inside the claim period"]
        s3["policy must belong to the patient"]
        s4["only DRAFT is editable · only DRAFT is deletable"]
    end

    subgraph L4["ClaimStatusMachine — workflow"]
        m1["which transitions are legal"]
        m2["what each transition requires"]
        m3["what each transition does to the money"]
        m4["writes the audit row"]
    end

    subgraph L5["Security — who may act"]
        r1["VIEWER: read only"]
        r2["BILLER: create, edit drafts, transition"]
        r3["ADMIN: everything, plus delete and reference data"]
    end

    L2 --> L3 --> L4 --> L1
    L5 -.->|"guards every mutation"| L3

    classDef hot fill:#e8effc,stroke:#1f5fd6,color:#123a8a;
    class L4 hot
```

---

## 16. What was deliberately left out of Phase 1

Not oversights. Each is a decision with a reason.

| Left out | Why | Where it goes |
|---|---|---|
| Real EDI 837/835 generation and parsing | X12 is a large specification and a demo does not need a valid interchange to show the workflow | Phase 2.1 / 2.2 |
| Payer connectivity (eligibility, claim status) | Needs credentials and a trading-partner agreement that a demo cannot have | Phase 2.3 |
| Appeals | Depends on denial reason codes, which arrive with the 835 | Phase 2.4 |
| File storage for attachments | Needs an object store and a retention policy decision | Phase 2.5 |
| AR aging and denial analytics | Cheap to add once the lifecycle data exists; adds nothing to a workflow demo | Phase 2.6 |
| Automated tests | The tests worth writing are integration tests against a real Postgres, which belongs with CI | Phase 3.1–3.3 |
| Refresh tokens, rate limiting, PHI access log | Production concerns, not workflow concerns | Phase 3.4–3.8 |
