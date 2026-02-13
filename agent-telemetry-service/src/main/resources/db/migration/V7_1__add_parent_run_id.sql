ALTER TABLE knowledge_run ADD COLUMN IF NOT EXISTS parent_run_id VARCHAR(36) NULL;
ALTER TABLE knowledge_run ADD COLUMN IF NOT EXISTS replay_mode VARCHAR(20) NULL;

ALTER TABLE knowledge_run ADD CONSTRAINT fk_run_parent
    FOREIGN KEY (parent_run_id) REFERENCES knowledge_run(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_run_parent ON knowledge_run(parent_run_id);
