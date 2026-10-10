-- V2: auditoria inmutable con cadena de hashes, idempotencia HTTP y outbox transaccional.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fiscal_relay_role') THEN
        CREATE ROLE fiscal_relay_role NOLOGIN;
    END IF;
END
$$;

-- ---------------------------------------------------------------- audit_event
CREATE TABLE audit_event (
    id               uuid PRIMARY KEY,
    tenant_id        uuid         NOT NULL REFERENCES tenant (id),
    seq              bigint       NOT NULL CHECK (seq >= 1),
    occurred_at      timestamptz  NOT NULL,
    actor_id         text         NOT NULL,
    actor_roles      text         NOT NULL,
    operation        varchar(100) NOT NULL,
    entity_type      varchar(50)  NOT NULL,
    entity_id        uuid,
    document_id      uuid,
    old_state        text,
    new_state        text,
    siat_request_ref  text,
    siat_response_ref text,
    ip               varchar(64),
    correlation_id   varchar(100),
    trace_id         varchar(100),
    prev_hash        char(64)     NOT NULL,
    hash             char(64)     NOT NULL,
    CONSTRAINT uq_audit_tenant_seq UNIQUE (tenant_id, seq)
);
CREATE INDEX ix_audit_entity ON audit_event (tenant_id, entity_type, entity_id);
CREATE INDEX ix_audit_document ON audit_event (tenant_id, document_id) WHERE document_id IS NOT NULL;

-- Evidencia inmutable: ni UPDATE, ni DELETE, ni TRUNCATE, ni siquiera para el propietario (salvo
-- deshabilitar el trigger, que requiere privilegio de propietario y queda detectado por la cadena de hashes).
CREATE FUNCTION audit_event_block_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_event es append-only (% no permitido)', TG_OP USING ERRCODE = '42501';
END
$$;
CREATE TRIGGER audit_event_immutable BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_block_mutation();
CREATE TRIGGER audit_event_no_truncate BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_block_mutation();

ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_event FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_event FOR ALL
    USING (tenant_id = app_current_tenant()) WITH CHECK (tenant_id = app_current_tenant());
GRANT SELECT, INSERT ON audit_event TO fiscal_app_role;

-- ---------------------------------------------------------------- idempotency_record
CREATE TABLE idempotency_record (
    tenant_id          uuid         NOT NULL REFERENCES tenant (id),
    idem_key           varchar(128) NOT NULL,
    request_hash       char(64)     NOT NULL,
    status             varchar(20)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    response_status    integer,
    response_location  text,
    response_body      text,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    completed_at       timestamptz,
    expires_at         timestamptz  NOT NULL DEFAULT now() + interval '48 hours',
    PRIMARY KEY (tenant_id, idem_key)
);
CREATE INDEX ix_idem_expires ON idempotency_record (expires_at);
ALTER TABLE idempotency_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_record FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON idempotency_record FOR ALL
    USING (tenant_id = app_current_tenant()) WITH CHECK (tenant_id = app_current_tenant());
GRANT SELECT, INSERT, UPDATE ON idempotency_record TO fiscal_app_role;

-- ---------------------------------------------------------------- outbox_event
CREATE TABLE outbox_event (
    id              uuid PRIMARY KEY,
    tenant_id       uuid         NOT NULL REFERENCES tenant (id),
    aggregate_type  varchar(50)  NOT NULL,
    aggregate_id    uuid         NOT NULL,
    event_type      varchar(100) NOT NULL,
    payload         jsonb        NOT NULL,
    status          varchar(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    last_error      text,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    published_at    timestamptz
);
CREATE INDEX ix_outbox_pending ON outbox_event (next_attempt_at, created_at) WHERE status = 'PENDING';
ALTER TABLE outbox_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_event FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON outbox_event FOR ALL
    USING (tenant_id = app_current_tenant()) WITH CHECK (tenant_id = app_current_tenant());
-- El relay (SET LOCAL ROLE fiscal_relay_role) ve y actualiza el outbox de todos los tenants, nada mas.
CREATE POLICY relay_select ON outbox_event FOR SELECT TO fiscal_relay_role USING (true);
CREATE POLICY relay_update ON outbox_event FOR UPDATE TO fiscal_relay_role USING (true) WITH CHECK (true);
GRANT SELECT, INSERT ON outbox_event TO fiscal_app_role;
GRANT SELECT ON outbox_event TO fiscal_relay_role;
GRANT UPDATE (status, attempts, next_attempt_at, last_error, published_at) ON outbox_event TO fiscal_relay_role;
GRANT USAGE ON SCHEMA public TO fiscal_relay_role;
