CREATE TABLE actor (
    id TEXT PRIMARY KEY,
    use_name TEXT NOT NULL,
    enrollment_state TEXT NOT NULL DEFAULT 'pending'
);

-- Create index on enrollment_state for potential filtering
CREATE INDEX idx_actor_enrollment_state ON actor(enrollment_state);