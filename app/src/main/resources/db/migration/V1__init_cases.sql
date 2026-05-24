-- V1: Inicijalna šema CBR baze
-- VLASNIK: Član 2 (CBR baza). Bilo koja izmena = nova migracija (V2__...sql)

CREATE TABLE IF NOT EXISTS cases (
    id                      BIGSERIAL PRIMARY KEY,
    source                  VARCHAR(20) NOT NULL,         -- JUDGMENT / USER
    source_judgment_id      VARCHAR(50),

    -- Ključne činjenice (cbr_key=true)
    substance_quantity_m3   DOUBLE PRECISION,
    substance_type          VARCHAR(50),
    pollution_target        VARCHAR(20),
    damage_extent           VARCHAR(30),
    intent                  VARCHAR(20),
    prior_conviction        BOOLEAN,
    remedied_damage         BOOLEAN,
    forest_area_ha          DOUBLE PRECISION,

    -- Ishod
    article_violated        VARCHAR(100),
    sentence_type           VARCHAR(30),
    sentence_months         INTEGER,

    -- Ostatak
    facts_json              TEXT,
    description             TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cases_article ON cases(article_violated);
CREATE INDEX idx_cases_source ON cases(source);
