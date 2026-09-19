# Claim Management System

Healthcare professional claim management: capture, workflow, audit trail and receivables.

**Java 21 · Spring Boot 4.1.1 · PostgreSQL 17 · React 19 + TypeScript · Docker Compose**

| Document | What's in it |
|---|---|
| [`PLAN.md`](PLAN.md) | The build plan, phase split and technology decisions |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 15 diagrams: context, containers, components, ERD, state machine, sequences, deployment |
| [`docs/PHASE2_HANDOFF.md`](docs/PHASE2_HANDOFF.md) | Specifications for the six features deliberately left unbuilt |
| [`docs/API.md`](docs/API.md) | Endpoint reference |

---

## Run it

Docker is the only prerequisite. No local JDK, Maven, Node or Postgres needed.

```bash
docker compose up --build
```

First build pulls images and resolves dependencies, so expect 3–5 minutes. After that, startup is about 30 seconds.

| | |
|---|---|
| **Application** | http://localhost:9000 |
| **Swagger UI** | http://localhost:9000/swagger-ui.html |
| **API, direct** | http://localhost:9090/api |
| **Health** | http://localhost:9090/actuator/health |
| **Postgres** | `localhost:54321`, database `claims`, user `claims`, password `claims` |

### Demo accounts

| Username | Password | Role | Can do |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | Everything, including deletes and reference-data changes |
| `biller` | `biller123` | BILLER | Create and edit drafts, drive status transitions |
| `dana` | `biller123` | BILLER | Same as above, a second biller for the audit trail |
| `viewer` | `viewer123` | VIEWER | Read only — the API rejects mutations, not just the UI |

### Stop it

```bash
docker compose down          # keep the data
docker compose down -v       # wipe the database volume and re-seed on next start
```

---

## Five-minute demo script

1. **Sign in** as `biller / biller123`. The dashboard shows 12 seeded claims spread across every status, with charged, collected and outstanding AR.

2. **Filter.** Go to *All claims*, filter to `DRAFT`. Two claims. Clear, filter by payer, filter by service date. Filters combine and are reflected in the URL, so a filtered view is shareable.

3. **Open `CLM-2026-000010`** — a draft. Note the audit trail at the bottom, and that the only available transitions are `SUBMITTED` and `VOID`, because the server computed those from the state machine.

4. **Submit it.** The claim moves to `SUBMITTED`, `submitted_at` is stamped, the badge and button set change, and a new row appears in the timeline. Walk it on: `ACCEPTED`, then `PAID` with an allowed amount of 265.00 and a paid amount of 212.00. Patient responsibility computes to 53.00 and outstanding drops to zero.

5. **Try to break it.** With the claim now `PAID`, `PUT /api/claims/10` returns **409**: *"Claim CLM-2026-000010 is PAID and can no longer be edited."* Terminal means terminal.

6. **Show the role boundary.** Sign in as `viewer / viewer123`. Action buttons are gone. Then prove it is not cosmetic:

   ```bash
   TOKEN=$(curl -s localhost:9090/api/auth/login -H 'Content-Type: application/json' \
     -d '{"username":"viewer","password":"viewer123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

   curl -i -X POST localhost:9090/api/claims/11/transition \
     -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"targetStatus":"SUBMITTED"}'
   # HTTP/1.1 403 Forbidden
   ```

7. **Show the Phase 2 boundary.** Open *Phase 2 roadmap* and press **Probe endpoint** on any card. Each returns a real `501` with `X-Phase: 2` and a pointer to the written spec. Open `CLM-2026-000004` (denied) and note that appeal is blocked with the same message. Nothing is silently missing.

---

## Running without Docker

```bash
# Postgres
docker run -d --name cms-db -p 54321:5432 \
  -e POSTGRES_DB=claims -e POSTGRES_USER=claims -e POSTGRES_PASSWORD=claims postgres:17-alpine

# Backend (Java 21+). DB_PORT must match the host port above.
cd backend && DB_PORT=54321 mvn spring-boot:run

# Frontend (Node 20+)
cd frontend && npm install && npm run dev     # http://localhost:5173, proxies /api to :9090
```

For the Vite dev server, set `CORS_ALLOWED_ORIGINS=http://localhost:5173` for the backend. Under Docker it stays empty because nginx proxies `/api`, so the browser sees a single origin.

---

## What Phase 1 covers

- **Claims** — create, edit, delete drafts; service lines with CPT/HCPCS codes, modifiers and units; ICD-10 diagnoses with sequencing; server-computed totals
- **Workflow** — a nine-state machine with guards, side effects and an append-only audit trail
- **Reference data** — patients, payers, providers, and coordination-of-benefits coverage
- **Search** — paged and filtered by status, payer, patient, provider, service date and free text
- **Security** — JWT authentication, three roles, method-level authorisation
- **Schema** — Flyway migrations, `CHECK` constraints on every coded field, `NUMERIC(12,2)` money
- **API docs** — OpenAPI 3 and Swagger UI, Phase 2 stubs included and marked
- **Docker** — one command, health-gated startup ordering, multi-stage images, non-root runtime

## What Phase 1 deliberately does not cover

EDI 837P export · ERA 835 remittance ingest · real-time eligibility (270/271) · denial management and appeals · document attachments · AR aging and denial analytics.

Each has a live route, an authorisation rule and a written specification. Each returns `501`. See [`docs/PHASE2_HANDOFF.md`](docs/PHASE2_HANDOFF.md).

---

## Project layout

```
claim-management/
├── docker-compose.yml          three services, health-gated ordering
├── .env.example                every value has a working default
├── PLAN.md                     the build plan
├── docs/
│   ├── ARCHITECTURE.md         15 Mermaid diagrams
│   ├── PHASE2_HANDOFF.md       specs for the unbuilt features
│   └── API.md                  endpoint reference
├── backend/
│   ├── Dockerfile              maven build stage → JRE alpine runtime, non-root
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/claire/claims/
│       │   ├── domain/         entities + ClaimStatus (owns the transition table)
│       │   ├── repository/     Spring Data JPA
│       │   ├── service/        ClaimService, ClaimStatusMachine, mappers, specifications
│       │   ├── web/            REST controllers
│       │   ├── dto/            Java records
│       │   ├── security/       JWT config, user details, current-user provider
│       │   ├── common/         exceptions + RFC 9457 handler
│       │   └── phase2/         the 501 stubs
│       └── resources/
│           ├── application.yml
│           └── db/migration/   V1__schema.sql, V2__seed.sql
└── frontend/
    ├── Dockerfile              node build stage → nginx alpine runtime
    ├── nginx.conf              SPA fallback + /api proxy
    └── src/
        ├── api/                typed client, RFC 9457 unwrapping
        ├── auth/               token handling and role context
        ├── components/         layout and shared UI
        └── pages/              dashboard, claims, patients, payers, providers, roadmap
```

---

## Design decisions worth knowing

**The state machine has one home.** `ClaimStatus` (an enum) owns the transition table. `ClaimStatusMachine` is the only class that changes a claim's status. `GET /api/reference/status-transitions` serialises that same enum, and the React UI renders its buttons from `claim.allowedTransitions` on the response. The UI cannot offer an action the API will refuse.

**Totals are never trusted from the client.** `claim.total_charge` is recomputed from the service lines on every write. The form shows a running total for the user's benefit; the server ignores it.

**Only drafts are editable.** Anything past `DRAFT` is an immutable billing record. Corrections go `REJECTED → DRAFT`, or void and re-file — the same discipline a real clearinghouse enforces. Attempts return 409 with an explanation.

**Errors have one shape.** Five layers can reject a request — JWT filter, role check, bean validation, business rules, database constraints — and all five return RFC 9457 `application/problem+json`. Validation failures carry per-field detail.

**`501` means "not built", not "broken".** Phase 2 routes exist and authorise correctly, then return 501 with `X-Phase: 2` and a pointer to their spec. A gap you can call is better than a gap you discover.

**Money is `NUMERIC(12,2)` and `BigDecimal`, start to finish.** No floating point anywhere in the billing path.

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Backend exits with `Schema-validation: missing column ...` | A JPA mapping disagrees with `V1__schema.sql`. The message names the table and column. This is `ddl-auto: validate` doing its job — fix the mapping, do not switch to `update`. |
| Backend cannot reach the database | `db` has a healthcheck and `backend` waits for it. If it still fails, `docker compose logs db` — usually a stale volume from an earlier schema. `docker compose down -v` and start again. |
| `Failed to resolve org.springdoc:...` during the image build | springdoc is the only dependency not version-managed by the Spring Boot parent. Delete that block from `backend/pom.xml` and `config/OpenApiConfig.java`; nothing else imports it. You lose Swagger UI and nothing else. |
| Port already in use | Set `FRONTEND_PORT`, `BACKEND_PORT` or `DB_PORT_HOST` in `.env`. |
| Frontend loads but every call 401s | The JWT expired (8 hours by default). Sign in again, or raise `JWT_TTL_MINUTES`. |
| `app.jwt.secret must be at least 32 bytes` | Your `JWT_SECRET` is too short for HS256. `openssl rand -base64 48`. |
