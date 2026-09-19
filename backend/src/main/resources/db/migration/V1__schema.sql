-- =====================================================================
-- Claim Management System - baseline schema
-- Healthcare professional (837P-shaped) claims.
-- All money is NUMERIC(12,2). Never floating point in a billing system.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Users / RBAC
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_user_role CHECK (role IN ('ADMIN', 'BILLER', 'VIEWER'))
);

-- ---------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------
CREATE TABLE payer (
    id              BIGSERIAL PRIMARY KEY,
    payer_code      VARCHAR(20)  NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    plan_type       VARCHAR(30)  NOT NULL,
    claims_address  VARCHAR(255),
    phone           VARCHAR(30),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_payer_plan_type CHECK (plan_type IN
        ('COMMERCIAL', 'MEDICARE', 'MEDICAID', 'TRICARE', 'WORKERS_COMP', 'SELF_PAY'))
);

CREATE TABLE provider (
    id          BIGSERIAL PRIMARY KEY,
    npi         VARCHAR(10)  NOT NULL UNIQUE,
    first_name  VARCHAR(80)  NOT NULL,
    last_name   VARCHAR(80)  NOT NULL,
    specialty   VARCHAR(80),
    tax_id      VARCHAR(20),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_provider_npi CHECK (npi ~ '^[0-9]{10}$')
);

CREATE TABLE patient (
    id            BIGSERIAL PRIMARY KEY,
    mrn           VARCHAR(30)  NOT NULL UNIQUE,
    first_name    VARCHAR(80)  NOT NULL,
    last_name     VARCHAR(80)  NOT NULL,
    date_of_birth DATE         NOT NULL,
    phone         VARCHAR(30),
    email         VARCHAR(160),
    address_line1 VARCHAR(160),
    city          VARCHAR(80),
    state         VARCHAR(2),
    postal_code   VARCHAR(10),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_patient_dob CHECK (date_of_birth <= CURRENT_DATE)
);

CREATE INDEX ix_patient_last_name ON patient (LOWER(last_name));

-- ---------------------------------------------------------------------
-- Coverage
-- ---------------------------------------------------------------------
CREATE TABLE insurance_policy (
    id               BIGSERIAL PRIMARY KEY,
    patient_id       BIGINT      NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    payer_id         BIGINT      NOT NULL REFERENCES payer (id),
    member_id        VARCHAR(50) NOT NULL,
    group_number     VARCHAR(50),
    priority         VARCHAR(10) NOT NULL,
    effective_date   DATE        NOT NULL,
    termination_date DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_policy_priority CHECK (priority IN ('PRIMARY', 'SECONDARY', 'TERTIARY')),
    CONSTRAINT ck_policy_dates    CHECK (termination_date IS NULL OR termination_date >= effective_date)
);

CREATE INDEX ix_policy_patient ON insurance_policy (patient_id);

-- ---------------------------------------------------------------------
-- Claims
-- ---------------------------------------------------------------------
CREATE TABLE claim (
    id                     BIGSERIAL PRIMARY KEY,
    claim_number           VARCHAR(24)   NOT NULL UNIQUE,
    patient_id             BIGINT        NOT NULL REFERENCES patient (id),
    provider_id            BIGINT        NOT NULL REFERENCES provider (id),
    payer_id               BIGINT        NOT NULL REFERENCES payer (id),
    policy_id              BIGINT        REFERENCES insurance_policy (id),
    status                 VARCHAR(20)   NOT NULL,
    service_date_from      DATE          NOT NULL,
    service_date_to        DATE          NOT NULL,
    place_of_service       VARCHAR(2)    NOT NULL DEFAULT '11',
    total_charge           NUMERIC(12,2) NOT NULL DEFAULT 0,
    allowed_amount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    paid_amount            NUMERIC(12,2) NOT NULL DEFAULT 0,
    patient_responsibility NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes                  VARCHAR(1000),
    submitted_at           TIMESTAMPTZ,
    created_by             VARCHAR(60)   NOT NULL,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_claim_status CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'REJECTED', 'PARTIALLY_PAID',
         'PAID', 'DENIED', 'APPEALED', 'VOID')),
    CONSTRAINT ck_claim_service_dates CHECK (service_date_to >= service_date_from),
    CONSTRAINT ck_claim_amounts CHECK (
        total_charge >= 0 AND allowed_amount >= 0
        AND paid_amount >= 0 AND patient_responsibility >= 0)
);

CREATE INDEX ix_claim_status       ON claim (status);
CREATE INDEX ix_claim_patient      ON claim (patient_id);
CREATE INDEX ix_claim_payer        ON claim (payer_id);
CREATE INDEX ix_claim_service_from ON claim (service_date_from);
-- Open AR is the hot query path; index only the rows that matter.
CREATE INDEX ix_claim_open ON claim (status, service_date_from)
    WHERE status NOT IN ('PAID', 'VOID');

CREATE TABLE claim_line (
    id             BIGSERIAL PRIMARY KEY,
    claim_id       BIGINT        NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    line_number    INTEGER       NOT NULL,
    cpt_code       VARCHAR(5)    NOT NULL,
    modifiers      VARCHAR(20),
    service_date   DATE          NOT NULL,
    units          INTEGER       NOT NULL DEFAULT 1,
    charge_amount  NUMERIC(12,2) NOT NULL,
    allowed_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    paid_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    description    VARCHAR(255),
    CONSTRAINT uq_claim_line UNIQUE (claim_id, line_number),
    CONSTRAINT ck_line_units  CHECK (units > 0),
    CONSTRAINT ck_line_charge CHECK (charge_amount >= 0),
    CONSTRAINT ck_line_cpt    CHECK (cpt_code ~ '^[0-9A-Z][0-9]{3}[0-9A-Z]$')
);

CREATE INDEX ix_claim_line_claim ON claim_line (claim_id);

CREATE TABLE claim_diagnosis (
    id          BIGSERIAL PRIMARY KEY,
    claim_id    BIGINT      NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    icd10_code  VARCHAR(10) NOT NULL,
    description VARCHAR(255),
    sequence_no INTEGER     NOT NULL,
    CONSTRAINT uq_claim_dx UNIQUE (claim_id, sequence_no),
    CONSTRAINT ck_dx_sequence CHECK (sequence_no BETWEEN 1 AND 12),
    CONSTRAINT ck_dx_icd10    CHECK (icd10_code ~ '^[A-TV-Z][0-9][0-9AB](\.[0-9A-Z]{1,4})?$')
);

CREATE INDEX ix_claim_dx_claim ON claim_diagnosis (claim_id);

-- Append-only audit trail. No UPDATE or DELETE is ever issued against this.
CREATE TABLE claim_status_history (
    id          BIGSERIAL PRIMARY KEY,
    claim_id    BIGINT      NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    reason      VARCHAR(500),
    changed_by  VARCHAR(60) NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_status_history_claim ON claim_status_history (claim_id, changed_at DESC);
