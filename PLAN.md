# Claim Management System — Build Plan

**Domain:** Healthcare / medical billing claims (professional claims, 837P-shaped)
**Stack:** Java 21 + Spring Boot 4.1.1 · React 19 + TypeScript + Vite · PostgreSQL 17 · Docker Compose
**Phases:** 3. Phase 1 delivers ~80% of the system and is fully runnable. Phase 2 (~15%) is deliberately stubbed for handoff to another agent. Phase 3 (~5%) is hardening.

---

## 1. Why these technology choices

| Choice | Reason | Rejected alternative |
|---|---|---|
| Spring Boot **4.1.1** | Current stable line (released 2026-06-30, OSS support to 2027-07-31). Requires Java 17–26. | 3.5.x — OSS support ended 2026-06-30. |
| Java **21** (LTS) | Broadest toolchain support; SB 4.1 accepts 17–26. Records, pattern matching, virtual threads all available. | Java 25 — newer, but buys nothing here and narrows the base image / IDE compatibility. |
| **PostgreSQL 17** | Proper `CHECK` constraints, partial indexes, `jsonb` for the EDI payloads Phase 2 needs. | MySQL — weaker constraint and JSON story for claims data. |
| **Flyway** migrations | Schema is versioned and reviewable. `ddl-auto=validate`, never `update`. | Hibernate `ddl-auto=update` — silently drifts, unacceptable for a claims schema. |
| **JWT** (jjwt) stateless auth | No session affinity, trivial to scale, easy for the React SPA. | Spring Session — extra infrastructure for no demo benefit. |
| Hand-written **CSS** (design tokens) | Zero build-chain risk; full control over the demo's appearance. | Tailwind/MUI — another versioned dependency that can break the build. |
| **nginx** serving the SPA + proxying `/api` | Single origin in the browser, so no CORS config and no hardcoded backend URL. | Vite dev server in prod — not a real deployment. |

### Known risk, stated up front
Spring Boot 4 moved to **Jackson 3** and **Spring Security 7**. Some third-party libraries lag that. The build is verified end to end before delivery; if `springdoc-openapi` or `jjwt` prove incompatible, the documented fallback is Spring Boot **3.5.16** with a one-line `pom.xml` change and no application-code changes.

---

## 2. Domain model

```
Payer ──< InsurancePolicy >── Patient
                                 │
Provider ─────────────────────< Claim >──< ClaimLine
                                 │   └──< ClaimDiagnosis
                                 └──< ClaimStatusHistory
AppUser (ADMIN | BILLER | VIEWER)
```

| Entity | Purpose | Key fields |
|---|---|---|
| `Patient` | Person receiving care | `mrn` (unique), name, DOB, contact, address |
| `Payer` | Insurance company | `payerCode` (unique), name, plan type, claims address |
| `Provider` | Rendering/billing provider | `npi` (unique, 10 digits), name, specialty, tax ID |
| `InsurancePolicy` | Patient↔Payer coverage | member ID, group no., effective/termination dates, `PRIMARY`/`SECONDARY` |
| `Claim` | The billable encounter | `claimNumber` (unique), service dates, place of service, `totalCharge`, `allowedAmount`, `paidAmount`, `patientResponsibility`, `status` |
| `ClaimLine` | One billed service | line no., **CPT/HCPCS** code, modifiers, units, charge, allowed, paid |
| `ClaimDiagnosis` | ICD-10 codes on the claim | `icd10Code`, description, sequence (1 = principal) |
| `ClaimStatusHistory` | Immutable audit trail | from/to status, actor, timestamp, reason |
| `AppUser` | Login + RBAC | username, BCrypt hash, role, enabled |

**Money is `NUMERIC(12,2)` / `BigDecimal` everywhere.** No floats in a billing system.
**Invariant:** `claim.totalCharge` must equal the sum of its line charges. Enforced in the service layer on every write.

---

## 3. Claim status state machine

```
        ┌──────────────── (correct) ────────────────┐
        │                                           │
      DRAFT ──submit──> SUBMITTED ──accept──> ACCEPTED ──pay──> PAID
        │                   │                     │
        │                   └──reject──> REJECTED ─┘        ACCEPTED ──deny──> DENIED
        │                                                                        │
        └──────────────────────── void ────────────────────────> VOID  <─────────┘
                                                                  ▲
                                                        (any non-terminal state)
```

| From | Allowed to | Role required |
|---|---|---|
| `DRAFT` | `SUBMITTED`, `VOID` | BILLER, ADMIN |
| `SUBMITTED` | `ACCEPTED`, `REJECTED`, `VOID` | BILLER, ADMIN |
| `REJECTED` | `DRAFT` (correct & resubmit), `VOID` | BILLER, ADMIN |
| `ACCEPTED` | `PAID`, `PARTIALLY_PAID`, `DENIED`, `VOID` | BILLER, ADMIN |
| `PARTIALLY_PAID` | `PAID`, `DENIED`, `VOID` | BILLER, ADMIN |
| `DENIED` | `APPEALED` *(Phase 2)*, `VOID` | BILLER, ADMIN |
| `PAID`, `VOID` | — terminal — | — |

Rules enforced centrally in `ClaimStatusMachine`:
- Only `DRAFT` claims are editable. Any other status rejects mutation with **409 Conflict**.
- Submitting requires ≥1 line, ≥1 diagnosis, and an active policy on the service date.
- Every transition writes a `ClaimStatusHistory` row. History is append-only.
- `VIEWER` role cannot transition anything.

---

## 4. API surface (Phase 1)

```
POST   /api/auth/login                    → { token, username, role, expiresAt }
GET    /api/auth/me

GET    /api/claims?status=&payerId=&patientId=&q=&from=&to=&page=&size=&sort=
POST   /api/claims
GET    /api/claims/{id}
PUT    /api/claims/{id}                   (DRAFT only)
DELETE /api/claims/{id}                   (DRAFT only, ADMIN)
POST   /api/claims/{id}/transition        { targetStatus, reason, paidAmount? }
GET    /api/claims/{id}/history
GET    /api/claims/summary                counts + totals by status

GET/POST/PUT   /api/patients  /api/patients/{id}
GET            /api/patients/{id}/policies
GET/POST/PUT   /api/payers    /api/payers/{id}
GET/POST/PUT   /api/providers /api/providers/{id}
POST           /api/policies       PUT /api/policies/{id}

GET    /api/reference/status-transitions   the state machine, as data
GET    /actuator/health
GET    /swagger-ui.html
```

Errors are RFC 9457 `application/problem+json` from a single `@RestControllerAdvice`. Validation failures return field-level detail.

---

## 5. Phase breakdown

### Phase 1 — ~80% · fully working, demoable  ✅ *delivered*

1. Repo, Maven module, Spring Boot 4.1.1 skeleton
2. `docker-compose.yml`: Postgres 17 + backend + nginx frontend, healthchecks, one command
3. Flyway `V1__schema.sql` (tables, constraints, indexes) + `V2__seed.sql` (4 users, 8 patients, 5 payers, 4 providers, policies, 12 claims spread across every status)
4. JPA entities, repositories, Specification-based search
5. JWT security: login, filter, `@PreAuthorize` role checks, BCrypt
6. `ClaimStatusMachine` + transition endpoint + append-only history
7. Claim / patient / payer / provider / policy CRUD with Bean Validation
8. Global problem-detail error handling
9. springdoc OpenAPI + Swagger UI
10. React SPA: login, claims list (server-side paging, status/payer/date/text filters), claim detail (lines, diagnoses, history timeline, transition actions gated by role), claim create & edit with live charge totalling, patient / payer / provider screens, summary dashboard tiles
11. nginx reverse proxy, production build
12. README with one-command run and demo script

### Phase 2 — ~15% · deliberately left for the demo handoff  🚧 *stubbed*

Each ships as a real endpoint that returns **501 Not Implemented** with a `X-Phase: 2` header, plus a written spec in `docs/PHASE2_HANDOFF.md`. Nothing is silently missing; every gap is discoverable from Swagger.

| # | Feature | Stub endpoint |
|---|---|---|
| 2.1 | **EDI 837P export** — generate an X12 837 Professional file for a batch of claims | `POST /api/edi/837p/export` |
| 2.2 | **ERA / 835 remittance ingest** — parse an 835, auto-post payments and adjustments | `POST /api/edi/835/import` |
| 2.3 | **Eligibility check (270/271)** — real-time coverage verification against a payer | `POST /api/eligibility/check` |
| 2.4 | **Denial management & appeals** — CARC/RARC codes, appeal letters, `DENIED → APPEALED` transition | `POST /api/claims/{id}/appeal`, `GET /api/denials` |
| 2.5 | **Document attachments** — upload/store/retrieve supporting docs on a claim | `POST /api/claims/{id}/attachments` |
| 2.6 | **Analytics & reporting** — AR aging buckets, denial rate by payer, days-in-AR, clean-claim rate | `GET /api/analytics/*` |

### Phase 3 — ~5% · hardening

3.1 Testcontainers integration tests over the full lifecycle · 3.2 unit tests for the state machine · 3.3 GitHub Actions CI · 3.4 refresh tokens + rotation · 3.5 rate limiting on `/api/auth/login` · 3.6 structured JSON logging + request correlation IDs · 3.7 Micrometer metrics · 3.8 PHI access audit log (HIPAA-shaped) · 3.9 `.env`-driven secrets, no defaults in prod profile.

---

## 6. Repository layout

```
claim-management/
├── README.md                 one-command run + demo script
├── PLAN.md                   this file
├── docker-compose.yml
├── .env.example
├── docs/
│   ├── PHASE2_HANDOFF.md     specs for the stubbed features
│   └── API.md
├── backend/
│   ├── Dockerfile            multi-stage: maven build → JRE runtime
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/claire/claims/
│       │   ├── config/  security/  common/
│       │   ├── domain/       entities + enums
│       │   ├── repository/
│       │   ├── service/      incl. ClaimStatusMachine
│       │   ├── web/          controllers
│       │   ├── dto/          Java records
│       │   └── phase2/       stub controllers
│       └── resources/
│           ├── application.yml
│           └── db/migration/ V1__schema.sql, V2__seed.sql
└── frontend/
    ├── Dockerfile            node build → nginx
    ├── nginx.conf            SPA fallback + /api proxy
    └── src/                  pages/ components/ api/ styles
```

## 7. Definition of done for Phase 1

- `docker compose up --build` from a clean checkout serves the app at `http://localhost:9000`.
- Login as `biller / biller123` and drive a claim `DRAFT → SUBMITTED → ACCEPTED → PAID` in the UI.
- Swagger UI lists every endpoint, Phase 2 stubs included and marked.
- Illegal transitions and edits to non-DRAFT claims return 409 with a readable message.
- `VIEWER` sees data but no action buttons, and the API rejects them too.
