-- Milestones 10-12: embedding vectors, practice sessions, adaptive turns, attempt <-> session link.
-- One statement per ALTER so the script runs on MySQL 8 and on H2 (tests).

ALTER TABLE question_embeddings ADD COLUMN vector_data BLOB;

ALTER TABLE interview_sessions ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE interview_sessions ADD COLUMN tag_ids VARCHAR(500);
ALTER TABLE interview_sessions ADD COLUMN difficulty VARCHAR(20);
ALTER TABLE interview_sessions ADD COLUMN question_limit INT NOT NULL DEFAULT 10;
ALTER TABLE interview_sessions ADD COLUMN seed_question_id BIGINT;
ALTER TABLE interview_sessions ADD CONSTRAINT fk_is_seed FOREIGN KEY (seed_question_id) REFERENCES questions (id) ON DELETE SET NULL;
CREATE INDEX ix_is_started ON interview_sessions (started_at);

ALTER TABLE interview_turns ADD COLUMN turn_type VARCHAR(20) NOT NULL DEFAULT 'BANK';
ALTER TABLE interview_turns ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ASKED';
ALTER TABLE interview_turns ADD COLUMN parent_turn_id BIGINT;
ALTER TABLE interview_turns ADD COLUMN expected_points TEXT;
ALTER TABLE interview_turns ADD COLUMN difficulty VARCHAR(20);
ALTER TABLE interview_turns ADD COLUMN topic_name VARCHAR(120);
ALTER TABLE interview_turns ADD COLUMN confidence VARCHAR(20);
ALTER TABLE interview_turns ADD COLUMN asked_at DATETIME(6);
ALTER TABLE interview_turns ADD COLUMN answered_at DATETIME(6);
ALTER TABLE interview_turns ADD CONSTRAINT fk_it_parent FOREIGN KEY (parent_turn_id) REFERENCES interview_turns (id) ON DELETE SET NULL;

ALTER TABLE question_attempts ADD COLUMN session_id BIGINT;
ALTER TABLE question_attempts ADD CONSTRAINT fk_qa_session FOREIGN KEY (session_id) REFERENCES interview_sessions (id) ON DELETE SET NULL;
CREATE INDEX ix_qa_attempted ON question_attempts (attempted_at);
