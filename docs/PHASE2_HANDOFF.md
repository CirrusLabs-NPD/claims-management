# Phase 2 handoff specification

This is the brief for the agent picking up Phase 2. Phase 1 is complete and runs on its own; everything here is deliberately unbuilt.

**How to find the gaps without reading this document:** every Phase 2 route already exists, enforces its authorisation rule, and returns `501 Not Implemented` with an `X-Phase: 2` header and a `handoffRef` pointing back to a section number here. They are listed in Swagger UI alongside the working endpoints, and the app's **Phase 2 roadmap** screen will call each one live.

---

## Ground rules for Phase 2 work

1. **The state machine stays in one place.** Any new transition goes in `ClaimStatus.TRANSITIONS` and its guard goes in `ClaimStatusMachine`. Do not add status logic to a controller or a React component.
2. **Money stays `BigDecimal` / `NUMERIC(12,2)`.** No doubles, anywhere, ever.
3. **Schema changes are Flyway migrations.** Add `V3__*.sql`, `V4__*.sql`. Never edit `V1` or `V2`; they have already run. `ddl-auto` stays `validate`.
4. **`claim_status_history` is append-only.** No feature may update or delete a row in it.
5. **Errors are RFC 9457.** Throw the exceptions in `ApiExceptions`; `GlobalExceptionHandler` does the rest. Do not hand-roll error bodies.
6. **Delete the stub when you implement it.** Remove the method from `Phase2Controller` and put the real endpoint in a proper controller, so `501` always means "still not built".
7. **Never invent clinical or financial data.** If a payer response is unavailable, surface the failure; do not fabricate an adjudication.

---

## 2.1 EDI 837P claim export

**Stub:** `POST /api/edi/837p/export` · roles `ADMIN`, `BILLER`

Generate an X12 837 Professional (005010X222A1) interchange for a batch of claims so they can be sent to a clearinghouse.

**Request**

```json
{ "claimIds": [8, 9], "payerId": 2, "testIndicator": true }
```

Accept either an explicit `claimIds` list or a filter (`payerId` + `status=SUBMITTED`). Reject any claim not in `SUBMITTED`.

**Response**

```json
{
  "batchId": "BATCH-2026-000014",
  "claimCount": 2,
  "totalCharge": 1795.00,
  "interchangeControlNumber": "000000123",
  "fileName": "837P_20260915_000123.txt",
  "content": "ISA*00*..."
}
```

**Segment mapping**

| X12 loop / segment | Source |
|---|---|
| ISA / GS | submitter config (new `app.edi.*` properties) |
| Loop 2010AA — billing provider | `provider.npi`, `provider.tax_id`, `provider` name |
| Loop 2010BA — subscriber | `patient` + `insurance_policy.member_id` |
| Loop 2010BB — payer | `payer.name`, `payer.payer_code` |
| Loop 2300 — claim | `claim_number` (CLM01), `total_charge` (CLM02), `place_of_service` (CLM05-1) |
| HI segment | `claim_diagnosis` ordered by `sequence_no`, principal first |
| Loop 2400 — service line | `claim_line.cpt_code`, `modifiers`, `units`, `charge_amount`, `service_date` |

**New schema**

```sql
CREATE TABLE edi_batch (
    id BIGSERIAL PRIMARY KEY,
    batch_number VARCHAR(24) NOT NULL UNIQUE,
    interchange_control_number VARCHAR(9) NOT NULL,
    claim_count INTEGER NOT NULL,
    total_charge NUMERIC(12,2) NOT NULL,
    payload TEXT NOT NULL,
    created_by VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE edi_batch_claim (
    batch_id BIGINT NOT NULL REFERENCES edi_batch (id) ON DELETE CASCADE,
    claim_id BIGINT NOT NULL REFERENCES claim (id),
    PRIMARY KEY (batch_id, claim_id)
);
```

**Acceptance criteria**

- Exporting a non-`SUBMITTED` claim returns 409 naming the claim and its status.
- The same claim cannot appear in two batches unless `allowRebill: true` is passed.
- Control numbers are strictly increasing and unique.
- Segment count in SE matches the segments actually written.

**Estimate:** 4 days. **Suggested library:** hand-rolled writer, or `com.imsweb:x12-parser`. Do not pull in a full clearinghouse SDK for this.

---

## 2.2 ERA / 835 remittance ingest

**Stub:** `POST /api/edi/835/import` (multipart `file`) · roles `ADMIN`, `BILLER`

Parse an 835 remittance advice, match it to claims, post money to the line level, and let the existing state machine move the claims.

**Processing**

1. Parse `CLP` segments. `CLP01` is the payer's claim control number; match it against `claim.claim_number`.
2. `CLP02` claim status code maps to the target status:

   | CLP02 | Meaning | Target status |
   |---|---|---|
   | 1 | processed as primary | `PAID` |
   | 2 | processed as secondary | `PAID` |
   | 3 | processed as tertiary | `PAID` |
   | 4 | denied | `DENIED` |
   | 19, 20, 21 | processed as *, forwarded | `PARTIALLY_PAID` |
   | 22 | reversal of previous payment | reversal, see below |

3. `CLP03` charge, `CLP04` paid, `CLP05` patient responsibility. Post to `claim`.
4. `SVC` segments carry line-level amounts. Match on `cpt_code` + `modifiers` and post to `claim_line.allowed_amount` / `paid_amount`.
5. `CAS` segments carry adjustments. Persist group code (`CO`, `PR`, `OA`, `PI`), reason code (CARC) and amount.
6. Drive each claim through `ClaimStatusMachine`, never by setting `status` directly. A transition the machine rejects is a row on the exception report, not a silent skip.

**New schema**

```sql
CREATE TABLE remittance (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    payer_id BIGINT REFERENCES payer (id),
    check_number VARCHAR(50),
    check_date DATE,
    total_paid NUMERIC(12,2) NOT NULL,
    claims_matched INTEGER NOT NULL,
    claims_unmatched INTEGER NOT NULL,
    raw_payload TEXT NOT NULL,
    imported_by VARCHAR(60) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE claim_adjustment (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    claim_line_id BIGINT REFERENCES claim_line (id) ON DELETE CASCADE,
    remittance_id BIGINT REFERENCES remittance (id),
    group_code VARCHAR(2) NOT NULL,     -- CO, PR, OA, PI
    reason_code VARCHAR(5) NOT NULL,    -- CARC, e.g. 197
    remark_code VARCHAR(5),             -- RARC, e.g. N130
    amount NUMERIC(12,2) NOT NULL,
    CONSTRAINT ck_adj_group CHECK (group_code IN ('CO','PR','OA','PI'))
);
```

**Acceptance criteria**

- Importing the same file twice is idempotent: matched on check number + payer, the second import is rejected with 409.
- Unmatched `CLP` segments appear on an exception report and block nothing.
- `SUM(claim_adjustment.amount) + paid = charge` per claim, or the import is flagged out of balance.
- Reversals (`CLP02 = 22`) create negative adjustments rather than editing history.

**Estimate:** 5 days. Depends on 2.1's parser groundwork.

---

## 2.3 Real-time eligibility (270/271)

**Stub:** `POST /api/eligibility/check` · roles `ADMIN`, `BILLER`

**Request**

```json
{ "patientId": 1, "payerId": 3, "serviceDate": "2026-09-20", "serviceTypeCode": "30" }
```

**Response**

```json
{
  "active": true,
  "checkedAt": "2026-09-15T14:02:11Z",
  "planBegin": "2023-04-01",
  "planEnd": null,
  "copay": 25.00,
  "coinsurancePercent": 20,
  "deductibleTotal": 1500.00,
  "deductibleRemaining": 340.00,
  "outOfPocketRemaining": 2100.00,
  "rawResponse": "ISA*00*..."
}
```

**Implementation notes**

- Put the payer call behind an `EligibilityGateway` interface with two implementations: a real HTTP client and a deterministic `StubEligibilityGateway` selected by Spring profile, so demos and tests never need payer credentials.
- Cache per patient + payer + service date for 24 hours. Eligibility calls are metered and often billed per transaction.
- Persist every check in an `eligibility_check` table. The response is evidence in a later appeal.
- **Wire it into the submit guard:** `ClaimStatusMachine.onSubmit` currently checks only that the policy dates cover the service date. Once this exists, it should also warn (not block) when the most recent eligibility check says inactive.

**Estimate:** 3 days. No dependency on 2.1 or 2.2, so this can run in parallel.

---

## 2.4 Denial management and appeals

**Stubs:** `POST /api/claims/{id}/appeal`, `GET /api/denials` · roles `ADMIN`, `BILLER`

The one Phase 2 item that changes the state machine. `ClaimStatus.DENIED` currently allows only `VOID`; `ClaimStatusMachine` explicitly throws `NotImplementedYetException` for `DENIED → APPEALED`.

**What to change**

1. In `ClaimStatus`: `TRANSITIONS.put(DENIED, EnumSet.of(APPEALED, VOID));`
2. In `ClaimStatusMachine`: delete the `NotImplementedYetException` branch and add an `onAppeal` guard — an appeal requires at least one recorded denial reason and a deadline that has not passed.
3. `APPEALED` already routes to `PAID`, `DENIED` and `VOID`, so no other transition work is needed.
4. The UI needs nothing: buttons are rendered from `claim.allowedTransitions`, which the server derives from the enum.

**New schema**

```sql
CREATE TABLE claim_appeal (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    level INTEGER NOT NULL DEFAULT 1,        -- 1 reconsideration, 2 formal, 3 external
    filed_on DATE NOT NULL,
    deadline DATE NOT NULL,
    narrative TEXT NOT NULL,
    outcome VARCHAR(20),                     -- UPHELD, OVERTURNED, PARTIAL, WITHDRAWN
    decided_on DATE,
    filed_by VARCHAR(60) NOT NULL,
    CONSTRAINT ck_appeal_level CHECK (level BETWEEN 1 AND 3)
);
```

**Denial worklist** — `GET /api/denials?payerId=&from=&to=&reasonCode=` returns denied claims joined to their `claim_adjustment` rows (from 2.2), sorted by appeal deadline ascending, with days-remaining computed. This is the screen a biller lives in, so it deserves the same filter quality as the claims list.

**Acceptance criteria**

- Appealing a claim that is not `DENIED` returns 409.
- Appealing past the deadline returns 400 naming the deadline.
- An appeal outcome of `OVERTURNED` drives `APPEALED → PAID` through the state machine, writing history.

**Estimate:** 4 days. Needs 2.2 for real CARC/RARC reason codes.

---

## 2.5 Document attachments

**Stubs:** `POST /api/claims/{id}/attachments` (multipart), `GET /api/claims/{id}/attachments`

**Constraints**

- Accept PDF, TIFF, JPEG, PNG. Reject everything else on **content sniffing**, not on the file extension.
- 25 MB per file (`client_max_body_size 25m` is already set in `nginx.conf`).
- Store bytes in object storage (S3 or MinIO), never in the database. The row holds the key, size, SHA-256 and content type.
- Attachments on a claim past `DRAFT` may be added but never removed: they are part of the billing record.
- Serve downloads through a short-lived presigned URL. A raw public bucket URL to a document containing PHI is a reportable breach.

```sql
CREATE TABLE claim_attachment (
    id BIGSERIAL PRIMARY KEY,
    claim_id BIGINT NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    attachment_type VARCHAR(30),   -- OPERATIVE_NOTE, REFERRAL, PRIOR_AUTH, ITEMISED_BILL, OTHER
    uploaded_by VARCHAR(60) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Estimate:** 2 days. No dependencies.

---

## 2.6 Analytics and reporting

**Stubs:** `GET /api/analytics/ar-aging`, `/denial-rate`, `/payer-performance`

These are read-only aggregates. Write them as native SQL projections; do not load entities to count them.

**AR aging** — buckets by days since `submitted_at`, for claims where `status NOT IN ('PAID','VOID')`. The partial index `ix_claim_open` already exists for exactly this query.

```json
{
  "asOf": "2026-09-15",
  "buckets": [
    { "label": "0-30",  "claimCount": 4, "amount": 2465.00 },
    { "label": "31-60", "claimCount": 2, "amount": 1195.00 },
    { "label": "61-90", "claimCount": 1, "amount":  540.00 },
    { "label": "90+",   "claimCount": 1, "amount": 1450.00 }
  ],
  "total": 5650.00
}
```

**Denial rate** — per payer: denied ÷ adjudicated, plus the top five CARC codes by amount. Needs 2.2 for the reason codes; until then, group by the free-text transition reason.

**Payer performance** — per payer: average days from `SUBMITTED` to `PAID` (from `claim_status_history`, not from `updated_at`), clean-claim rate (share reaching `PAID` with no intervening `REJECTED` or `DENIED`), and average allowed ÷ charged.

**Acceptance criteria**

- Every figure reconciles against the claims list filtered the same way. If the dashboard and the list disagree, the dashboard is wrong.
- Each endpoint answers in under 200 ms on 100,000 claims. Add indexes as needed in a `V*__.sql` migration.

**Estimate:** 3 days. Needs 2.2 for the denial half; AR aging can be built immediately.

---

## Suggested sequencing

```
Week 1   2.3 eligibility  +  2.5 attachments        (independent, unblock quickly)
Week 2   2.1 EDI 837P export                        (builds the X12 groundwork)
Week 3   2.2 ERA 835 ingest                         (depends on 2.1)
Week 4   2.4 denials and appeals  +  2.6 analytics  (both depend on 2.2)
```

## Phase 3, after the above

| # | Item |
|---|---|
| 3.1 | Testcontainers integration tests across the full claim lifecycle |
| 3.2 | Unit tests for every edge of `ClaimStatusMachine` |
| 3.3 | GitHub Actions CI: build, test, publish images |
| 3.4 | Refresh tokens with rotation |
| 3.5 | Rate limiting on `POST /api/auth/login` |
| 3.6 | Structured JSON logging with request correlation IDs |
| 3.7 | Micrometer metrics and a Grafana dashboard |
| 3.8 | PHI access audit log: who read which patient, and when |
| 3.9 | All secrets from the environment, no working defaults in the production profile |
