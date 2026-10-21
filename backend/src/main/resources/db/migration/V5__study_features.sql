-- Study features: topic notes, learning paths, flashcard attempts, interview plans.
-- One statement per ALTER so the script runs on MySQL 8 and on H2 (tests).

ALTER TABLE topics ADD COLUMN notes TEXT;
ALTER TABLE topics ADD COLUMN notes_updated_at DATETIME(6);

CREATE TABLE learning_path_steps (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  topic_id   BIGINT NOT NULL,
  tag_id     BIGINT NOT NULL,
  step_order INT NOT NULL,
  CONSTRAINT uk_lps_topic_tag UNIQUE (topic_id, tag_id),
  CONSTRAINT fk_lps_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
  CONSTRAINT fk_lps_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);
CREATE INDEX ix_lps_topic_order ON learning_path_steps (topic_id, step_order);

-- PRACTICE (graded in a session) or FLASHCARD (self-graded knew / shaky / didn't know)
ALTER TABLE question_attempts ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'PRACTICE';

CREATE TABLE interview_plans (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  title          VARCHAR(200) NOT NULL,
  company_id     BIGINT,
  interview_date DATE NOT NULL,
  daily_target   INT,
  notes          VARCHAR(2000),
  status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at     DATETIME(6) NOT NULL,
  CONSTRAINT fk_ip_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE SET NULL
);
CREATE INDEX ix_ip_date ON interview_plans (status, interview_date);

CREATE TABLE interview_plan_topics (
  plan_id  BIGINT NOT NULL,
  topic_id BIGINT NOT NULL,
  PRIMARY KEY (plan_id, topic_id),
  CONSTRAINT fk_ipt_plan FOREIGN KEY (plan_id) REFERENCES interview_plans (id) ON DELETE CASCADE,
  CONSTRAINT fk_ipt_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE
);
