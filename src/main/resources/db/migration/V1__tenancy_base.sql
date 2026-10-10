-- V1: base multi-tenant (tenant, company, branch, point_of_sale) con Row Level Security.
-- Se ejecuta con el usuario propietario (fiscal_owner). La aplicacion se conecta como un
-- miembro de fiscal_app_role, que NO es dueno de las tablas ni tiene BYPASSRLS (ADR-0002).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fiscal_app_role') THEN
        CREATE ROLE fiscal_app_role NOLOGIN;
    END IF;
END
$$;

-- Tenant actual de la transaccion. Sin valor => NULL => ninguna fila visible (fail-closed).
CREATE FUNCTION app_current_tenant() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid $$;

CREATE TABLE tenant (
    id         uuid PRIMARY KEY,
    name       varchar(200) NOT NULL,
    status     varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    version    bigint       NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE company (
    id           uuid PRIMARY KEY,
    tenant_id    uuid         NOT NULL REFERENCES tenant (id),
    nit          bigint       NOT NULL CHECK (nit BETWEEN 1 AND 9999999999999),
    razon_social varchar(200) NOT NULL,
    environment  smallint     NOT NULL CHECK (environment IN (1, 2)),   -- 1 produccion, 2 pruebas/piloto
    modality     smallint     NOT NULL CHECK (modality IN (1, 2, 3)),   -- 1 electronica, 2 computarizada, 3 portal web
    system_code  varchar(100),                                           -- codigoSistema otorgado por el SIN
    status       varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    version      bigint       NOT NULL DEFAULT 0,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_tenant_nit_env UNIQUE (tenant_id, nit, environment),
    CONSTRAINT uq_company_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE branch (
    id              uuid PRIMARY KEY,
    tenant_id       uuid         NOT NULL,
    company_id      uuid         NOT NULL,
    codigo_sucursal integer      NOT NULL CHECK (codigo_sucursal >= 0),  -- 0 = Casa Matriz
    nombre          varchar(200) NOT NULL,
    direccion       varchar(500) NOT NULL,
    municipio       varchar(200) NOT NULL,
    telefono        varchar(50),
    version         bigint       NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_branch_company_codigo UNIQUE (company_id, codigo_sucursal),
    CONSTRAINT uq_branch_tenant_id UNIQUE (tenant_id, id),
    -- FK compuesta: una sucursal solo puede colgar de una empresa del MISMO tenant.
    CONSTRAINT fk_branch_company FOREIGN KEY (tenant_id, company_id) REFERENCES company (tenant_id, id)
);

CREATE TABLE point_of_sale (
    id                   uuid PRIMARY KEY,
    tenant_id            uuid         NOT NULL,
    branch_id            uuid         NOT NULL,
    codigo_punto_venta   integer      NOT NULL CHECK (codigo_punto_venta >= 1),
    nombre               varchar(200) NOT NULL,
    tipo_punto_venta     integer,                                         -- catalogo SIAT, se valida contra catalogo en S5
    version              bigint       NOT NULL DEFAULT 0,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_pos_branch_codigo UNIQUE (branch_id, codigo_punto_venta),
    CONSTRAINT uq_pos_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_pos_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES branch (tenant_id, id)
);

-- Row Level Security: forzada incluso para el propietario.
ALTER TABLE tenant ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_select ON tenant FOR SELECT USING (id = app_current_tenant());
CREATE POLICY tenant_update ON tenant FOR UPDATE USING (id = app_current_tenant()) WITH CHECK (id = app_current_tenant());
-- Alta de tenant: operacion de plataforma (SUPER_ADMIN, protegida en la capa de seguridad en S2).
CREATE POLICY tenant_insert ON tenant FOR INSERT WITH CHECK (true);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['company', 'branch', 'point_of_sale'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I FOR ALL USING (tenant_id = app_current_tenant()) WITH CHECK (tenant_id = app_current_tenant())', t);
    END LOOP;
END
$$;

-- Sin DELETE: la informacion fiscal no se borra.
GRANT USAGE ON SCHEMA public TO fiscal_app_role;
GRANT SELECT, INSERT, UPDATE ON tenant, company, branch, point_of_sale TO fiscal_app_role;
GRANT EXECUTE ON FUNCTION app_current_tenant() TO fiscal_app_role;
