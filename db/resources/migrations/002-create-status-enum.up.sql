CREATE TYPE status_enum AS ENUM ('active', 'inactive', 'pending');

CREATE TABLE entity (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    status status_enum NOT NULL DEFAULT 'pending',
    metadata JSONB,
    settings JSON
);
