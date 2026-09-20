CREATE TABLE projects (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    screen VARCHAR(200) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    current_checkpoint VARCHAR(64) NOT NULL DEFAULT 'cp_000',
    reference_w INTEGER NOT NULL DEFAULT 375,
    reference_h INTEGER NOT NULL DEFAULT 667,
    reference_unit VARCHAR(16) NOT NULL DEFAULT 'dp',
    layout VARCHAR(32) NOT NULL DEFAULT 'relative',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE nodes (
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    node_id VARCHAR(64) NOT NULL,
    node_key VARCHAR(240) NOT NULL,
    type VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checksum VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    PRIMARY KEY (project_id, node_id),
    UNIQUE (project_id, node_key)
);

CREATE TABLE checkpoints (
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    checkpoint VARCHAR(64) NOT NULL,
    snapshot TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (project_id, checkpoint)
);

CREATE TABLE assets (
    ref VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    original_name VARCHAR(500) NOT NULL,
    mime VARCHAR(100) NOT NULL,
    byte_count BIGINT NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX assets_project_idx ON assets(project_id);

CREATE TABLE artifacts (
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(500) NOT NULL,
    target VARCHAR(32) NOT NULL,
    mime VARCHAR(100) NOT NULL,
    byte_count BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (project_id, name)
);
