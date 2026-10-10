-- V4: CUIS (365 dias) y CUFD (24 h) con historico. Un unico registro ACTIVE por (sucursal, POS).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fiscal_job_role') THEN
        CREATE ROLE fiscal_job_role NOLOGIN;
    END IF;
END
$$;

CREATE TABLE cuis (
    id            uuid PRIMARY KEY,
    tenant_id     uuid         NOT NULL,
    company_id    uuid         NOT NULL,
    branch_id     uuid         NOT NULL,
    pos_id        uuid,
    codigo        varchar(100) NOT NULL,
    valid_until   timestamptz  NOT NULL,
    status        varchar(12)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    requested_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_cuis_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES branch (tenant_id, id),
    CONSTRAINT fk_cuis_pos    FOREIGN KEY (tenant_id, pos_id)    REFERENCES point_of_sale (tenant_id, id),
    CONSTRAINT uq_cuis_tenant_id UNIQUE (tenant_id, id)
);
-- Un CUIS ACTIVE por alcance (pos NULL = nivel sucursal).
CREATE UNIQUE INDEX uq_cuis_active_scope ON cuis (branch_id, COALESCE(pos_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status = 'ACTIVE';
CREATE INDEX ix_cuis_scope ON cuis (tenant_id, branch_id, pos_id, requested_at DESC);

CREATE TABLE cufd (
    id             uuid PRIMARY KEY,
    tenant_id      uuid         NOT NULL,
    company_id     uuid         NOT NULL,
    branch_id      uuid         NOT NULL,
    pos_id         uuid,
    cuis_id        uuid         NOT NULL,
    codigo         varchar(300) NOT NULL,
    codigo_control varchar(100) NOT NULL,
    direccion      varchar(500),
    valid_until    timestamptz  NOT NULL,
    status         varchar(12)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    requested_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT fk_cufd_cuis FOREIGN KEY (tenant_id, cuis_id) REFERENCES cuis (tenant_id, id),
    CONSTRAINT fk_cufd_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES branch (tenant_id, id),
    CONSTRAINT fk_cufd_pos FOREIGN KEY (tenant_id, pos_id) REFERENCES point_of_sale (tenant_id, id),
    CONSTRAINT uq_cufd_cuis_codigo UNIQUE (cuis_id, codigo)
);
CREATE UNIQUE INDEX uq_cufd_active_scope ON cufd (branch_id, COALESCE(pos_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status = 'ACTIVE';
CREATE INDEX ix_cufd_scope ON cufd (tenant_id, branch_id, pos_id, requested_at DESC);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['cuis', 'cufd'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I FOR ALL USING (tenant_id = app_current_tenant()) WITH CHECK (tenant_id = app_current_tenant())', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE ON %I TO fiscal_app_role', t);
    END LOOP;
END
$$;

-- Rol de jobs (SET LOCAL ROLE fiscal_job_role): SOLO LECTURA entre tenants para decidir que alcances renovar.
-- La renovacion en si corre despues con el tenant fijado y bajo RLS normal.
GRANT USAGE ON SCHEMA public TO fiscal_job_role;
GRANT SELECT ON company, branch, point_of_sale, cuis, cufd TO fiscal_job_role;
GRANT SELECT (company_id, status, expires_at) ON fiscal_token TO fiscal_job_role;
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['company', 'branch', 'point_of_sale', 'cuis', 'cufd', 'fiscal_token'] LOOP
        EXECUTE format('CREATE POLICY job_select ON %I FOR SELECT TO fiscal_job_role USING (true)', t);
    END LOOP;
END
$$;
