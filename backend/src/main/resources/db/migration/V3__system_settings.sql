-- App-wide switches an admin changes at runtime. Always exactly one row (id = 1).
CREATE TABLE system_settings (
    id                  SMALLINT     PRIMARY KEY CHECK (id = 1),
    keep_alive_enabled  BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO system_settings (id) VALUES (1);
