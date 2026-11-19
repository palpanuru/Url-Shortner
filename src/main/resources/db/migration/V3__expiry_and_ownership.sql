-- V3 (ambiguous requirement: "links should be safe and shouldn't live forever").
-- Every link gets an expiry and an owning API client.

ALTER TABLE short_link ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE short_link ADD COLUMN owner_id VARCHAR(64);

-- Backfill existing links. Decision (see docs/scenarios/03-ambiguous.md, A-4): default 365-day
-- life from creation, but never less than 30 days from the migration, so no existing link
-- breaks on deploy day.
UPDATE short_link
   SET expires_at = GREATEST(created_at + INTERVAL '365' DAY,
                             CURRENT_TIMESTAMP + INTERVAL '30' DAY)
 WHERE expires_at IS NULL;

-- Pre-existing links had no owner; assign a sentinel so no live client can manage them until an
-- operator reassigns them explicitly.
UPDATE short_link SET owner_id = 'legacy' WHERE owner_id IS NULL;

ALTER TABLE short_link ALTER COLUMN expires_at SET NOT NULL;
ALTER TABLE short_link ALTER COLUMN owner_id SET NOT NULL;

CREATE INDEX ix_short_link_expires_at ON short_link (expires_at);
