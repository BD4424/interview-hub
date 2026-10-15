# Interview Prep Hub

Personal interview-preparation platform: a de-duplicated, company-attributed question bank with multi-format import,
and (in later milestones) an AI interviewer built on Spring AI.

> **Status: Milestones 1–7 delivered (core bank, companies, tags, exact de-duplication, CSV import with company
> attribution, duplicate review).** AI features, other file formats, practice sessions and progress are *not* built yet —
> see [Milestone status](#milestone-status). Read [Verification](#verification-what-was-and-was-not-run) before relying on this.

## Repository layout

```
interview-prep-hub/
├── README.md
├── .env.example
├── samples/                       sample CSVs for each attribution mode (illustrative data only)
├── backend/                       Spring Boot 3.3 / Java 17 / Maven — modular monolith
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/prephub/
│       │   ├── common/            TextNormalizer (canonical text + SHA-256), enums, errors, paging
│       │   ├── config/            CORS
│       │   ├── topic/             primary topics (CRUD, fallback "General")
│       │   ├── tag/               tags (normalized, race-safe find-or-create, usage counts)
│       │   ├── company/           company directory + company summary endpoint
│       │   ├── question/          Question, QuestionCompany (M:N with metadata), filters, writer, merge
│       │   ├── importer/          parsers → QuestionCandidate, attribution resolver, classification, review
│       │   └── dashboard/         dashboard statistics
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/      Flyway V1 (full schema) + V2 (seed topics)
│       └── test/                  unit tests + @SpringBootTest integration tests (H2 in MySQL mode)
└── frontend/                      Angular 18 standalone components, Reactive Forms, HttpClient
    ├── package.json, angular.json, tsconfig*.json, proxy.conf.json
    └── src/app/
        ├── core/                  models.ts, api.service.ts
        └── features/              dashboard, question list/card/form, companies, company detail,
                                   topics & tags, import wizard, placeholders for later modules
```

## Entity relationships

```
topics 1──* questions *──* tags            (question_tags)
              │
              ├──* question_companies *──1 companies     UNIQUE(question_id, company_id) + role/round/level/date/evidence/status
              ├──* question_relations (parent/child follow-ups)
              ├──* question_attempts, question_bookmarks(1:1), question_embeddings(1:1)
              └──  questions.fingerprint UNIQUE  (SHA-256 of canonical text)

import_jobs 1──* import_candidates ──? questions (matched_question_id, created_question_id; SET NULL on delete)
interview_sessions 1──* interview_turns ──? questions (nullable: session-only follow-ups)
```

Deletion policy: deleting a question cascades to its tags/company links/attempts/bookmarks; deleting a topic or a
company that is still referenced is refused (`RESTRICT`); import history survives question deletion (`SET NULL`).

## Key rules implemented

* **Exact duplicates**: NFKC → collapse whitespace → lowercase → strip trailing `?.!:;` → SHA-256. Enforced by a
  unique DB constraint; inserts run in their own transaction and a constraint race is converted to a `409
  DUPLICATE_QUESTION` response containing `matchType`, `matchingQuestionId`, `matchingText`.
* **Companies & tags** are normalized (`" AMAZON "` = `amazon`), unique in the DB, and created race-safely.
* **Company attribution precedence on import**: `NONE` ignores company columns entirely · otherwise an explicit row
  value wins · else the file-level default · else unknown. Multiple companies per cell use `;`. Nothing is inferred
  from question text. The active policy is shown in the preview.
* **Duplicate import handling (never silently drops data)**:
  * same question, nothing new → `AUTO_SKIPPED`;
  * same question with new companies/tags/answer → `NEEDS_REVIEW`; actions: *Merge*, *Add company only*, *Reject*,
    *Edit & re-validate*. Merges are additive: existing associations are never overwritten, an empty answer is filled,
    a differing answer is preserved in the question's notes;
  * same question twice inside one file → second row is merged into the first on approval;
  * exact duplicates cannot be "kept separate" (that would violate the canonical-question rule); that action is for
    semantic matches (milestone 10).
* Re-importing a file is detected by file hash and never creates new canonical questions.
* Imports are processed asynchronously; the UI polls the job, so no page reload is needed.
* Failed/rejected rows download as a CSV in the import layout (with formula-injection neutralised).
* Uploads: extension allow-list, 10 MB limit, binary sniffing, filenames sanitised, file never written to disk.
* Angular renders all imported text via interpolation (auto-escaped); no `innerHTML`.

## Local setup

Prerequisites: JDK 17+, Maven 3.9+, MySQL 8+, Node 18.19+/20+.

```sql
CREATE DATABASE prephub CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'prephub'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL ON prephub.* TO 'prephub'@'localhost';
```

```bash
# backend (Flyway creates the schema on first start)
cd backend
export DB_USER=prephub DB_PASSWORD=change-me      # PowerShell: $env:DB_USER="prephub"
mvn test                                          # unit + integration tests (H2)
mvn spring-boot:run                               # http://localhost:8080

# frontend
cd ../frontend
npm install
npm start                                         # http://localhost:4200, /api proxied to :8080
```

Try it: Companies → add "Amazon" → Import → `samples/01-single-company.csv` with *One company for the whole file*.
Then import `samples/02-per-row-companies.csv` with *Per row* — the HashMap row shows up as **Needs review**
(same question, new metadata). Then `samples/03-no-company-with-errors.csv` with *No attribution* to see invalid rows,
the failed-rows download and another duplicate.

## API (v1)

| Area | Endpoints |
|---|---|
| Topics | `GET/POST /topics`, `PUT/DELETE /topics/{id}` |
| Tags | `GET /tags[?q=]` (usage-sorted), `POST`, `PUT/DELETE /tags/{id}` |
| Companies | `GET /companies[?q=]`, `POST`, `GET/PUT/DELETE /companies/{id}`, `GET /companies/{id}/summary`, `GET /companies/{id}/questions` |
| Questions | `GET /questions?q&topicId&companyId&tagIds&matchAllTags&difficulty&type&hasAnswer&sort&page&size`, `POST`, `GET/PUT/DELETE /questions/{id}` |
| Associations | `POST /questions/{id}/companies`, `DELETE /questions/{id}/companies/{companyId}` |
| Imports | `POST /imports` (multipart: `file`, `attributionMode`, `defaultCompanyId`, `defaultTopicId`, `tags`), `GET /imports`, `GET /imports/{id}`, `GET /imports/{id}/candidates[?status=]`, `POST /imports/{id}/approve`, `POST /imports/{id}/candidates/{cid}/resolve`, `PUT /imports/{id}/candidates/{cid}`, `GET /imports/{id}/failed-rows.csv` |
| Dashboard | `GET /dashboard` |

## Milestone status

| # | Milestone | Status |
|---|---|---|
| 1 | Spring Boot backend + MySQL schema (Flyway) | Done — full schema incl. tables for later milestones |
| 2 | Topics, tags, companies, question CRUD | Done |
| 3 | Exact duplicate detection | Done |
| 4 | Company–question associations | Done |
| 5 | Angular dashboard + question bank | Done (practice/bookmark widgets pending M11–12) |
| 6 | CSV import, file/row-level attribution | Done (automatic header mapping; **manual column-mapping UI not built**) |
| 7 | Import preview + duplicate review | Done |
| 8 | XLSX / DOCX / PDF / TXT / MD parsers | **Not started** (parsers will emit the same `QuestionCandidate`) |
| 9 | Spring AI follow-up generation | **Not started** |
| 10 | Embedding-based semantic duplicates | **Not started** — `SemanticDuplicateDetector` interface + no-op bean in place |
| 11 | Interview sessions, answer evaluation, bookmarks | **Not started** (tables exist) |
| 12 | Progress tracking, deployment docs | **Not started** |

Acceptance criteria covered so far: 1–9. Criteria 10–14 depend on later milestones / a local build.

## Verification: what was and was not run

The generation sandbox had JDK 17 and Node, but **no Maven, no Angular CLI and no network access to Maven Central or
npm**. Therefore:

* **Run and passing**: the framework-free core (`TextNormalizer`, `CsvReader`, `CsvQuestionParser`,
  `CompanyAttributionResolver`, lenient enum parsing) was compiled with `javac` and exercised by a 22-assertion harness
  mirroring the JUnit unit tests.
* **Not run**: `mvn test` / `mvn spring-boot:run` (Spring, JPA, Flyway, the integration tests), and `npm install` /
  `ng build`. The code was reviewed carefully but has **not been compiled as a whole**. Expect to fix small issues on
  first build; please report the output and they can be fixed quickly.
