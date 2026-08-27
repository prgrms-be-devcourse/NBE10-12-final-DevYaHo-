ALTER TABLE group_buy_event_outbox
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
