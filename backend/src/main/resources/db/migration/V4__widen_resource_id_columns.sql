-- Image IDs come back from docker-java as "sha256:<64hex>" (71 chars),
-- which overflows the original VARCHAR(64). Widen to 128 to fit any
-- prefix-qualified id (sha256, etc.) and leave headroom for future formats.

ALTER TABLE docker_resources ALTER COLUMN resource_id TYPE VARCHAR(128);
ALTER TABLE activity_log     ALTER COLUMN resource_id TYPE VARCHAR(128);
