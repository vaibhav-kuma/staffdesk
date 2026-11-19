CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE TABLE app_users (
 id uuid PRIMARY KEY, email varchar(254) NOT NULL UNIQUE,
 password_hash varchar(100) NOT NULL,
 role varchar(16) NOT NULL CHECK(role IN ('ADMIN','EDITOR','VIEWER')),
 enabled boolean NOT NULL DEFAULT true, token_version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(), CHECK(email = lower(trim(email)))
);
CREATE TABLE employees (
 id uuid PRIMARY KEY, first_name varchar(80) NOT NULL, last_name varchar(80) NOT NULL,
 email varchar(254) NOT NULL UNIQUE, department varchar(80) NOT NULL, job_title varchar(120) NOT NULL,
 phone varchar(32), hire_date date NOT NULL,
 status varchar(16) NOT NULL CHECK(status IN ('ACTIVE','ON_LEAVE','INACTIVE')),
 version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 CHECK(email = lower(trim(email))), CHECK(length(trim(first_name)) > 0), CHECK(length(trim(last_name)) > 0)
);
CREATE INDEX employees_department_idx ON employees(department);
CREATE INDEX employees_status_idx ON employees(status);
CREATE INDEX employees_last_name_idx ON employees(last_name,id);
CREATE INDEX employees_search_idx ON employees USING gin ((lower(first_name || ' ' || last_name || ' ' || email)) gin_trgm_ops);
CREATE TABLE audit_events (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 actor_id uuid NOT NULL, action varchar(64) NOT NULL, entity_id uuid NOT NULL,
 occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_time_idx ON audit_events(occurred_at);
CREATE TABLE login_attempts (
 identity_hash varchar(64) PRIMARY KEY, attempts integer NOT NULL,
 window_start timestamptz NOT NULL, blocked_until timestamptz
);
CREATE INDEX login_attempts_window_idx ON login_attempts(window_start);
