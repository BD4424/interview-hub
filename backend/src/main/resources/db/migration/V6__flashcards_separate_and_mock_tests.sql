-- 1) Flashcards get their own history. Reviews no longer count as answered questions, scores or streaks.
CREATE TABLE flashcard_reviews (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  question_id BIGINT NOT NULL,
  grade       VARCHAR(10) NOT NULL,
  reviewed_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_fr_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
);
CREATE INDEX ix_fr_question ON flashcard_reviews (question_id, reviewed_at);
CREATE INDEX ix_fr_reviewed ON flashcard_reviews (reviewed_at);

-- move flashcard ratings recorded by the previous version (scores 9 / 5 / 2) out of question_attempts
INSERT INTO flashcard_reviews (question_id, grade, reviewed_at)
  SELECT question_id, CASE WHEN score >= 8 THEN 'KNEW' WHEN score >= 4 THEN 'SHAKY' ELSE 'DIDNT' END, attempted_at
  FROM question_attempts WHERE mode = 'FLASHCARD';
DELETE FROM question_attempts WHERE mode = 'FLASHCARD';

-- 2) Full-length mock tests: answer everything, scored after submission.
CREATE TABLE mock_tests (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  title              VARCHAR(200) NOT NULL,
  scope              VARCHAR(20) NOT NULL,
  topic_ids          VARCHAR(500),
  difficulty         VARCHAR(20),
  question_count     INT NOT NULL,
  time_limit_minutes INT,
  status             VARCHAR(30) NOT NULL,
  evaluation_mode    VARCHAR(10),
  started_at         DATETIME(6) NOT NULL,
  deadline_at        DATETIME(6),
  submitted_at       DATETIME(6),
  completed_at       DATETIME(6),
  overall_score      DOUBLE
);
CREATE INDEX ix_mt_status ON mock_tests (status, started_at);

CREATE TABLE mock_test_items (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  test_id          BIGINT NOT NULL,
  item_no          INT NOT NULL,
  question_id      BIGINT,
  question_text    TEXT NOT NULL,
  topic_name       VARCHAR(120),
  difficulty       VARCHAR(20),
  user_answer      TEXT,
  flagged          BOOLEAN NOT NULL DEFAULT FALSE,
  status           VARCHAR(20) NOT NULL,
  score            DOUBLE,
  feedback         TEXT,
  error_message    VARCHAR(500),
  answered_at      DATETIME(6),
  evaluated_at     DATETIME(6),
  attempt_recorded BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT uk_mti_no UNIQUE (test_id, item_no),
  CONSTRAINT fk_mti_test FOREIGN KEY (test_id) REFERENCES mock_tests (id) ON DELETE CASCADE,
  CONSTRAINT fk_mti_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE SET NULL
);
CREATE INDEX ix_mti_status ON mock_test_items (test_id, status);
