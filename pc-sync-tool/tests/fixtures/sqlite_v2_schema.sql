PRAGMA foreign_keys = ON;

CREATE TABLE sync_metadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE categories (
    category_code TEXT PRIMARY KEY,
    category_name TEXT NOT NULL,
    source_order INTEGER NOT NULL
);

CREATE TABLE units (
    unit_code TEXT PRIMARY KEY,
    unit_name TEXT NOT NULL,
    source_order INTEGER NOT NULL
);

CREATE TABLE products (
    source_product_key TEXT PRIMARY KEY,
    gid TEXT NOT NULL,
    barcode TEXT NOT NULL,
    secondary_barcode TEXT,
    name TEXT NOT NULL,
    category_code TEXT,
    unit_code TEXT,
    sale_price_raw TEXT NOT NULL,
    sale_price_decimal TEXT NOT NULL,
    simple_price_raw TEXT,
    simple_price_decimal TEXT,
    simple_threshold_raw TEXT,
    simple_threshold_decimal TEXT,
    stop_flag INTEGER NOT NULL,
    source_update_time_local TEXT,
    FOREIGN KEY (category_code) REFERENCES categories(category_code) ON DELETE RESTRICT,
    FOREIGN KEY (unit_code) REFERENCES units(unit_code) ON DELETE RESTRICT
);

CREATE TABLE promotion_candidates (
    candidate_id TEXT PRIMARY KEY,
    source_type TEXT NOT NULL,
    source_key TEXT NOT NULL,
    verification_status TEXT NOT NULL CHECK (verification_status IN ('VERIFIED', 'UNVERIFIED', 'INVALID', 'UNKNOWN_TYPE', 'INACTIVE')),
    begin_local TEXT,
    end_local TEXT,
    normalized_rule_version TEXT,
    evidence_hash TEXT
);

CREATE TABLE promotion_candidate_products (
    mapping_id TEXT PRIMARY KEY,
    candidate_id TEXT NOT NULL,
    source_product_key TEXT,
    source_barcode TEXT,
    group_code TEXT,
    mapping_order INTEGER,
    FOREIGN KEY (candidate_id) REFERENCES promotion_candidates(candidate_id) ON DELETE CASCADE,
    FOREIGN KEY (source_product_key) REFERENCES products(source_product_key) ON DELETE RESTRICT
);

CREATE TABLE promotion_raw_rows (
    raw_row_id TEXT PRIMARY KEY,
    candidate_id TEXT,
    source_table TEXT NOT NULL,
    source_key TEXT NOT NULL,
    source_order INTEGER NOT NULL,
    original_json TEXT NOT NULL,
    canonical_json TEXT NOT NULL,
    FOREIGN KEY (candidate_id) REFERENCES promotion_candidates(candidate_id) ON DELETE CASCADE
);

CREATE TABLE promotion_rules (
    rule_id TEXT PRIMARY KEY,
    candidate_id TEXT NOT NULL UNIQUE,
    rule_type TEXT NOT NULL,
    rule_version TEXT NOT NULL,
    evidence_hash TEXT NOT NULL,
    parameters_json TEXT NOT NULL,
    priority_order INTEGER,
    stack_mode TEXT NOT NULL,
    FOREIGN KEY (candidate_id) REFERENCES promotion_candidates(candidate_id) ON DELETE CASCADE
);

CREATE TABLE promotion_rule_tiers (
    tier_id TEXT PRIMARY KEY,
    rule_id TEXT NOT NULL,
    threshold_decimal TEXT NOT NULL,
    value_kind TEXT NOT NULL,
    value_decimal TEXT NOT NULL,
    tier_order INTEGER NOT NULL,
    FOREIGN KEY (rule_id) REFERENCES promotion_rules(rule_id) ON DELETE CASCADE
);

CREATE TABLE promotion_rule_schedules (
    schedule_id TEXT PRIMARY KEY,
    rule_id TEXT NOT NULL,
    begin_date_local TEXT,
    end_date_local TEXT,
    weekday INTEGER,
    begin_time_local TEXT,
    end_time_local TEXT,
    schedule_order INTEGER NOT NULL,
    FOREIGN KEY (rule_id) REFERENCES promotion_rules(rule_id) ON DELETE CASCADE
);

CREATE TABLE promotion_rule_groups (
    group_id TEXT PRIMARY KEY,
    rule_id TEXT NOT NULL,
    group_code TEXT NOT NULL,
    required_count_decimal TEXT NOT NULL,
    group_order INTEGER NOT NULL,
    FOREIGN KEY (rule_id) REFERENCES promotion_rules(rule_id) ON DELETE CASCADE
);

CREATE TABLE validation_issues (
    issue_id TEXT PRIMARY KEY,
    severity TEXT NOT NULL,
    issue_code TEXT NOT NULL,
    candidate_id TEXT,
    source_product_key TEXT,
    message_code TEXT NOT NULL,
    diagnostic_json TEXT NOT NULL,
    FOREIGN KEY (candidate_id) REFERENCES promotion_candidates(candidate_id) ON DELETE SET NULL,
    FOREIGN KEY (source_product_key) REFERENCES products(source_product_key) ON DELETE SET NULL
);

CREATE INDEX idx_products_barcode ON products(barcode);
CREATE INDEX idx_candidate_products_candidate ON promotion_candidate_products(candidate_id, mapping_order);
CREATE INDEX idx_raw_rows_candidate ON promotion_raw_rows(candidate_id, source_order);
CREATE INDEX idx_tiers_rule ON promotion_rule_tiers(rule_id, tier_order);
CREATE INDEX idx_schedules_rule ON promotion_rule_schedules(rule_id, schedule_order);
CREATE INDEX idx_groups_rule ON promotion_rule_groups(rule_id, group_order);
CREATE INDEX idx_issues_candidate ON validation_issues(candidate_id);
