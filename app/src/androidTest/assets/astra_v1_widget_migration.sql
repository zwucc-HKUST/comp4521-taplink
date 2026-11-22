-- Frozen Astra v1 schema, including the tables created on first use of Extras.

CREATE TABLE cards (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    cents INTEGER NOT NULL DEFAULT 0 CHECK (cents >= 0),
    frozen INTEGER NOT NULL DEFAULT 0 CHECK (frozen IN (0, 1))
);

CREATE TABLE entries (
    id INTEGER PRIMARY KEY,
    card_id INTEGER NOT NULL REFERENCES cards(id),
    label TEXT NOT NULL,
    category TEXT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('Payment', 'Top-up', 'Reload')),
    delta INTEGER NOT NULL CHECK (
        (kind = 'Payment' AND delta < 0) OR (kind != 'Payment' AND delta > 0)
    ),
    after_cents INTEGER NOT NULL CHECK (after_cents >= 0),
    at INTEGER NOT NULL,
    operation TEXT NOT NULL,
    leg INTEGER NOT NULL CHECK (leg = 0 OR (leg = 1 AND kind = 'Reload')),
    UNIQUE (operation, leg)
);

CREATE INDEX entries_by_time ON entries(at DESC, id DESC);

CREATE TABLE inbox (
    id INTEGER PRIMARY KEY,
    event_key TEXT NOT NULL UNIQUE,
    text TEXT NOT NULL,
    at INTEGER NOT NULL
);

CREATE TABLE prefs (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    selected INTEGER NOT NULL REFERENCES cards(id),
    threshold INTEGER NOT NULL CHECK (threshold >= 0),
    reload INTEGER NOT NULL CHECK (reload > 0),
    auto INTEGER NOT NULL CHECK (auto IN (0, 1)),
    alerts INTEGER NOT NULL CHECK (alerts IN (0, 1)),
    hidden INTEGER NOT NULL CHECK (hidden IN (0, 1)),
    theme TEXT NOT NULL,
    reminders INTEGER NOT NULL CHECK (reminders IN (0, 1)),
    shake INTEGER NOT NULL CHECK (shake IN (0, 1))
);

CREATE TABLE IF NOT EXISTS extras_pass (
    operation TEXT PRIMARY KEY NOT NULL, card_id INTEGER NOT NULL, entry_id INTEGER NOT NULL UNIQUE,
    start_day INTEGER NOT NULL, end_day INTEGER NOT NULL, UNIQUE(card_id, start_day));

CREATE TABLE IF NOT EXISTS extras_claim (
    card_id INTEGER NOT NULL, month TEXT NOT NULL, spend INTEGER NOT NULL, credit INTEGER NOT NULL,
    claimed_at INTEGER NOT NULL, entry_id INTEGER NOT NULL UNIQUE, PRIMARY KEY(card_id, month));

CREATE TABLE IF NOT EXISTS extras_coupon (
    offer_id TEXT PRIMARY KEY NOT NULL, expires_day INTEGER NOT NULL,
    favorite INTEGER NOT NULL DEFAULT 0, redeemed_at INTEGER);

INSERT INTO cards VALUES (5, 'Reserve', 2500, 1), (17, 'Travel', 5200, 0);
INSERT INTO prefs VALUES (1, 17, 315, 490, 1, 0, 0, 'Dark', 0, 1);
INSERT INTO entries VALUES
    (71,  5, 'Deposit', 'Funding', 'Top-up', 2500, 2500, 1704067200000, 'fund-reserve', 0),
    (72, 17, 'Deposit', 'Funding', 'Top-up', 10000, 10000, 1704067200000, 'fund-travel', 0),
    (73, 17, 'Demo monthly pass', 'Transport', 'Payment', -6000, 4000, 1704067201000, 'extras.pass.17.fixture', 0),
    (74, 17, 'Fictional transport subsidy', 'Subsidy', 'Top-up', 1200, 5200, 1704067202000, 'extras.claim.17.2024-01', 0);
INSERT INTO inbox VALUES (31, 'reminder/pass', 'Check your pass.', 1704067203000);
INSERT INTO extras_pass VALUES ('extras.pass.17.fixture', 17, 73, 19723, 19754);
INSERT INTO extras_claim VALUES (17, '2024-01', 6000, 1200, 1704067202000, 74);
INSERT INTO extras_coupon VALUES
    ('paper', 19736, 0, NULL),
    ('tea', 19743, 1, 1704067204000),
    ('tram', 19752, 0, NULL);

PRAGMA user_version = 1;
