-- Interview Prep Hub - initial schema (MySQL 8+). Full schema created up front so later milestones add no breaking changes.

CREATE TABLE topics (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(120) NOT NULL,
  slug        VARCHAR(140) NOT NULL,
  description VARCHAR(1000),
  CONSTRAINT uk_topics_slug UNIQUE (slug)
);

CREATE TABLE tags (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  name            VARCHAR(120) NOT NULL,
  normalized_name VARCHAR(120) NOT NULL,
  description     VARCHAR(1000),
  CONSTRAINT uk_tags_normalized UNIQUE (normalized_name)
);

CREATE TABLE companies (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  name            VARCHAR(200) NOT NULL,
  normalized_name VARCHAR(200) NOT NULL,
  website         VARCHAR(500),
  industry        VARCHAR(200),
  notes           VARCHAR(2000),
  CONSTRAINT uk_companies_normalized UNIQUE (normalized_name)
);

CREATE TABLE questions (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  question_text    TEXT NOT NULL,
  canonical_text   TEXT NOT NULL,
  fingerprint      CHAR(64) NOT NULL,
  topic_id         BIGINT NOT NULL,
  difficulty       VARCHAR(20),
  question_type    VARCHAR(30),
  answer_text      TEXT,
  answer_status    VARCHAR(20) NOT NULL,
  source_type      VARCHAR(20) NOT NULL,
  source_reference VARCHAR(500),
  notes            TEXT,
  approval_status  VARCHAR(20) NOT NULL,
  created_at       DATETIME(6) NOT NULL,
  updated_at       DATETIME(6) NOT NULL,
  CONSTRAINT uk_questions_fingerprint UNIQUE (fingerprint),
  CONSTRAINT fk_questions_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE RESTRICT
);
CREATE INDEX ix_questions_topic ON questions (topic_id);
CREATE INDEX ix_questions_difficulty ON questions (difficulty);
CREATE INDEX ix_questions_approval ON questions (approval_status);

CREATE TABLE question_tags (
  question_id BIGINT NOT NULL,
  tag_id      BIGINT NOT NULL,
  PRIMARY KEY (question_id, tag_id),
  CONSTRAINT fk_qt_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
  CONSTRAINT fk_qt_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);
CREATE INDEX ix_qt_tag ON question_tags (tag_id);

CREATE TABLE question_companies (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  question_id        BIGINT NOT NULL,
  company_id         BIGINT NOT NULL,
  role               VARCHAR(200),
  interview_round    VARCHAR(100),
  job_level          VARCHAR(100),
  date_asked         DATE,
  source_reference   VARCHAR(500),
  attribution_status VARCHAR(20) NOT NULL,
  notes              VARCHAR(2000),
  CONSTRAINT uk_qc UNIQUE (question_id, company_id),
  CONSTRAINT fk_qc_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
  CONSTRAINT fk_qc_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE RESTRICT
);
CREATE INDEX ix_qc_company ON question_companies (company_id);

CREATE TABLE question_relations (
  parent_question_id BIGINT NOT NULL,
  child_question_id  BIGINT NOT NULL,
  relation_type      VARCHAR(30) NOT NULL,
  sequence_number    INT,
  PRIMARY KEY (parent_question_id, child_question_id),
  CONSTRAINT fk_qr_parent FOREIGN KEY (parent_question_id) REFERENCES questions (id) ON DELETE CASCADE,
  CONSTRAINT fk_qr_child FOREIGN KEY (child_question_id) REFERENCES questions (id) ON DELETE CASCADE
);

CREATE TABLE import_jobs (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  filename           VARCHAR(255) NOT NULL,
  file_hash          CHAR(64) NOT NULL,
  format             VARCHAR(10) NOT NULL,
  attribution_mode   VARCHAR(20) NOT NULL,
  default_company_id BIGINT,
  default_topic_id   BIGINT,
  status             VARCHAR(20) NOT NULL,
  total_records      INT NOT NULL DEFAULT 0,
  imported_count     INT NOT NULL DEFAULT 0,
  duplicate_count    INT NOT NULL DEFAULT 0,
  failed_count       INT NOT NULL DEFAULT 0,
  error_message      VARCHAR(2000),
  created_at         DATETIME(6) NOT NULL,
  completed_at       DATETIME(6),
  CONSTRAINT fk_ij_company FOREIGN KEY (default_company_id) REFERENCES companies (id) ON DELETE SET NULL,
  CONSTRAINT fk_ij_topic FOREIGN KEY (default_topic_id) REFERENCES topics (id) ON DELETE SET NULL
);
CREATE INDEX ix_ij_hash ON import_jobs (file_hash);

CREATE TABLE import_candidates (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  import_job_id       BIGINT NOT NULL,
  source_reference    VARCHAR(100) NOT NULL,
  raw_question        TEXT,
  fingerprint         CHAR(64),
  proposed_answer     TEXT,
  proposed_topic      VARCHAR(120),
  proposed_tags       VARCHAR(2000),
  proposed_companies  VARCHAR(2000),
  proposed_difficulty VARCHAR(20),
  proposed_type       VARCHAR(30),
  interview_round     VARCHAR(100),
  role                VARCHAR(200),
  job_level           VARCHAR(100),
  match_type          VARCHAR(30) NOT NULL,
  matched_question_id BIGINT,
  similarity_score    DOUBLE,
  review_status       VARCHAR(30) NOT NULL,
  rejection_reason    VARCHAR(1000),
  created_question_id BIGINT,
  CONSTRAINT fk_ic_job FOREIGN KEY (import_job_id) REFERENCES import_jobs (id) ON DELETE CASCADE,
  CONSTRAINT fk_ic_match FOREIGN KEY (matched_question_id) REFERENCES questions (id) ON DELETE SET NULL,
  CONSTRAINT fk_ic_created FOREIGN KEY (created_question_id) REFERENCES questions (id) ON DELETE SET NULL
);
CREATE INDEX ix_ic_job_status ON import_candidates (import_job_id, review_status);

CREATE TABLE interview_sessions (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_type  VARCHAR(20) NOT NULL,
  topic_id      BIGINT,
  company_id    BIGINT,
  started_at    DATETIME(6) NOT NULL,
  completed_at  DATETIME(6),
  overall_score DECIMAL(4,2),
  CONSTRAINT fk_is_topic FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE SET NULL,
  CONSTRAINT fk_is_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE SET NULL
);

CREATE TABLE interview_turns (
  id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id             BIGINT NOT NULL,
  question_id            BIGINT,
  question_text_snapshot TEXT NOT NULL,
  user_answer            TEXT,
  ai_feedback            TEXT,
  score                  DECIMAL(4,2),
  turn_number            INT NOT NULL,
  CONSTRAINT uk_turn UNIQUE (session_id, turn_number),
  CONSTRAINT fk_it_session FOREIGN KEY (session_id) REFERENCES interview_sessions (id) ON DELETE CASCADE,
  CONSTRAINT fk_it_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE SET NULL
);

CREATE TABLE question_attempts (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  question_id  BIGINT NOT NULL,
  user_answer  TEXT,
  score        DECIMAL(4,2),
  confidence   VARCHAR(20),
  feedback     TEXT,
  attempted_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_qa_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
);
CREATE INDEX ix_qa_question ON question_attempts (question_id, attempted_at);

CREATE TABLE question_bookmarks (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  question_id BIGINT NOT NULL,
  notes       VARCHAR(2000),
  created_at  DATETIME(6) NOT NULL,
  CONSTRAINT uk_bookmark_question UNIQUE (question_id),
  CONSTRAINT fk_qb_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
);

CREATE TABLE question_embeddings (
  question_id   BIGINT PRIMARY KEY,
  model         VARCHAR(100) NOT NULL,
  dimensions    INT NOT NULL,
  vector_ref    VARCHAR(255),
  embedded_text_fingerprint CHAR(64) NOT NULL,
  created_at    DATETIME(6) NOT NULL,
  CONSTRAINT fk_qe_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
);
