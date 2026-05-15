CREATE TABLE IF NOT EXISTS destinations (
    name            VARCHAR(255) PRIMARY KEY,
    type            VARCHAR(16)  NOT NULL,
    mode            VARCHAR(16),
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
    id                  UUID PRIMARY KEY,
    destination_name    VARCHAR(255) NOT NULL REFERENCES destinations(name) ON DELETE CASCADE,
    dest_type           VARCHAR(16)  NOT NULL,
    payload             BYTEA        NOT NULL,
    priority            INT          NOT NULL,
    offset_val          BIGINT       NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_destination_status_offset
    ON messages(destination_name, status, offset_val);

CREATE INDEX IF NOT EXISTS idx_messages_status
    ON messages(status);

CREATE TABLE IF NOT EXISTS consumer_offsets (
    client_id       VARCHAR(255) NOT NULL,
    topic_name      VARCHAR(255) NOT NULL REFERENCES destinations(name) ON DELETE CASCADE,
    sent_offset     BIGINT       NOT NULL,
    PRIMARY KEY (client_id, topic_name)
);

