CREATE TABLE region (
    code VARCHAR(2) PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE province (
    code        VARCHAR(2) PRIMARY KEY,
    name        TEXT       NOT NULL,
    region_code VARCHAR(2) NOT NULL REFERENCES region (code)
);

CREATE TABLE municipality (
    ine_code        VARCHAR(5) PRIMARY KEY CHECK (ine_code ~ '^[0-9]{5}$'),
    name            TEXT       NOT NULL,
    name_normalized TEXT       NOT NULL,
    province_code   VARCHAR(2) NOT NULL REFERENCES province (code)
);
CREATE INDEX municipality_name_normalized_idx ON municipality (name_normalized);

CREATE TABLE import_run (
    id          BIGSERIAL PRIMARY KEY,
    source      TEXT        NOT NULL,
    year        INT         NOT NULL,
    source_ref  TEXT,
    dry_run     BOOLEAN     NOT NULL,
    status      TEXT        NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    stats       JSONB       NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE holiday (
    id               BIGSERIAL PRIMARY KEY,
    holiday_date     DATE       NOT NULL,
    scope            TEXT       NOT NULL CHECK (scope IN ('NATIONAL', 'REGIONAL', 'LOCAL')),
    kind             TEXT       NOT NULL CHECK (kind IN ('NATIONAL_FIXED', 'NATIONAL_SUBSTITUTABLE', 'REGIONAL', 'LOCAL')),
    region_code      VARCHAR(2) REFERENCES region (code),
    municipality_ine VARCHAR(5) REFERENCES municipality (ine_code),
    name             TEXT       NOT NULL,
    import_run_id    BIGINT REFERENCES import_run (id),
    CONSTRAINT holiday_scope_target CHECK (
        (scope = 'NATIONAL' AND region_code IS NULL AND municipality_ine IS NULL)
        OR (scope = 'REGIONAL' AND region_code IS NOT NULL AND municipality_ine IS NULL)
        OR (scope = 'LOCAL' AND municipality_ine IS NOT NULL AND region_code IS NULL))
);
CREATE UNIQUE INDEX holiday_unique_idx
    ON holiday (holiday_date, scope, COALESCE(region_code, ''), COALESCE(municipality_ine, ''));
CREATE INDEX holiday_date_idx ON holiday (holiday_date);

CREATE TABLE coverage (
    year          INT        NOT NULL,
    region_code   VARCHAR(2) NOT NULL REFERENCES region (code),
    level         TEXT       NOT NULL CHECK (level IN ('REGIONAL', 'LOCAL')),
    status        TEXT       NOT NULL CHECK (status IN ('OFFICIAL', 'PROVISIONAL')),
    import_run_id BIGINT REFERENCES import_run (id),
    PRIMARY KEY (year, region_code, level)
);
