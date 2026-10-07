[中文](README.md) | [English](README.en.md)

<img src="frontend/public/brand/logo.jpg" width="80" alt="ZhiHua Technology official logo">

# ZhiHua TrainFlow · Employee Training and Competency Assessment

**Public source learning edition 0.1.0 · Non-commercial use.**

ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.) · [Official website](https://www.zhuatech.cn/).

TrainFlow records which course version an employee studied, exam results, who reviewed practical evidence and when internal competency expires. Small-business training leads, department supervisors and software implementation teams can evaluate course preparation, independent publication, assigned learning, server grading and practical review in an isolated learning environment.

The project's own code uses the existing [non-commercial source license](LICENSE), for individual learning, research and non-commercial exchange. Commercial use requires written authorization. This is not an OSI open-source license. [Third-party notices](THIRD_PARTY_NOTICES.md) retain each dependency's own terms.

## Workflow and users

```text
Course draft + four-choice questions → independent publication → assigned learner/reviewer
Learner reading acknowledgement → learner exam submission → server grading
Failed exam → retry within remaining attempts / terminal failure
Passed exam → directly qualified without practical assessment / designated practical review
Valid internal competency → date expiry → new training task
Revocation with a reason retains original grades and dates
```

Publication freezes content, questions, correct answers, pass score, attempts and validity days. Publishing a new edition archives the former edition; existing tasks keep the original content and exam results. Course family codes are instance-wide unique, edition numbers increase per code and at most one unpublished new edition exists per code.

| Role/module | Implemented operations |
| --- | --- |
| Training manager | Own draft CRUD, 2–30 four-choice questions, code/version maintenance, archive/revise, assign tasks, cancel unfinished training and revoke competency |
| Training reviewer | Independently publish another author's version; review assigned practical evidence as pass/fail, without reviewing self or self-assigned tasks |
| Learner | SELF scope, own assigned courses/tasks, reading acknowledgement, full answers, retries, history and authorized JSON export |
| Department/all-scope roles | Database-paginated course/task search by title/code, status filters, oldest/newest sorting, scoped statistics, expiry/overdue indicators and audit |
| System administrator | Users, roles/permissions, departments, registered navigation, permission names, training classifications/settings and account password management |
| Transaction safeguards | Shared write lock, versions and lowercase UUID keys bound to actor/action/full payload; exact retries do not consume another exam attempt |

Each question has four distinct options and one answer. Questions have equal weight; the server floors the percentage correct to an integer. All answers must be supplied. Learner detail/list/export never includes standard answers; non-SELF accounts with course-edit permission may view published compilation material. Scores are not accepted from the client.

A learner cannot receive a duplicate unfinished or still-valid task for the same course edition. Failed/cancelled/revoked/expired tasks allow retraining. Due dates include that date and assignments allow today through730days ahead; overdue tasks cannot acknowledge reading, take exams or receive practical review. Cancel/reassign with reasons rather than backdating history. Competency validity starts from server Shanghai date and includes the first and final day; one-day validity means the qualification date itself.

ALL permissions do not let an administrator take another person's exam or act for the designated reviewer. SELF users cannot compile/publish courses or assign other users even if extra write permissions are assigned. Expiry is computed on read, without a scheduler rewriting historical records. Practical failure retains written scores and completes the failed task; competency revocation retains grades and original dates.

### Limits and unfinished integrations

Reading acknowledgement records an active submission; it does not prove reading duration or identity. Exams have no timer, randomized questions or proctoring. Competency records are internal training data, not legal qualifications, professional certificates or electronic signatures. Practical evidence is a textual reference, with actual checks performed by the designated person.

Video, uploads, live courses, SCORM, question import, randomized/proctored exams, signed certificates, legal qualification verification, external notifications, multi-tenancy, automatic roster-eligibility sync, ERP/HR connectors and AI scoring are not implemented. No third-party business account or paid model is required. Core workflows run locally; the deployer supplies course content.

Single-enterprise/multi-department learning deployment uses a shared write lock under READ COMMITTED and rereads current account/role after locking. Course/task mutations, attempts, history and audit commit atomically. Lists default20/max100; selectors/workbench/statistics are bounded at10,000, with statistics rejecting excess. Audit reads the latest1,000 entries; one task preserves at most10exam attempts and a course at most30questions. Large deployments need separate concurrency, capacity, security and recovery assessment.

## Actual running pages

These current MySQL-backed pages contain explicitly fictional `TEST` records. Login authenticates roles; workbench lists own learning/review tasks; courses manage frozen editions; learning handles reading/exams/results; account/role pages maintain access and statistics aggregate authorized records.

| Page | Screenshot |
| --- | --- |
| Login | ![Login](docs/screenshots/login.jpg) |
| Learner workspace | ![Own workbench](docs/screenshots/workbench.jpg) |
| Course editions | ![Courses](docs/screenshots/courses.jpg) |
| Exam and results | ![Learning](docs/screenshots/learning.jpg) |
| Accounts | ![Accounts](docs/screenshots/accounts.jpg) |
| Roles and scope | ![Roles](docs/screenshots/roles.jpg) |
| Training statistics | ![Statistics](docs/screenshots/dashboard.jpg) |

## Architecture, requirements and layout

Vue3 → same-origin Nginx → Spring Boot4/Security/JPA → MySQL8.4. Flyway V1 identity, V2 training and V3 question-edit revision tracking; JPA validates without altering tables. Sessions are server-side and business records persist in MySQL.

| Requirement | Version |
| --- | --- |
| Local infrastructure | Docker Engine27+, Compose2+, at least4GB available memory and official registry access |
| Backend | Java21, Maven3.9, Spring Boot4.0.7, Security/JPA/Flyway, MariaDB JDBC3.5.10 connecting to MySQL |
| Frontend | Node24.19.0+, npm11, Vue3.5.40/Vite8.1.5, locked dependencies |
| Data/acceptance | MySQL8.4, Python3.11+ |

```text
backend/src/main/java/cn/zhuatech/trainflow/  Identity, course, task, grading and review
backend/src/main/resources/db/migration/    V1 identity, V2 training, V3 question revision
backend/src/test/                            Policy and HTTP integration tests
frontend/src/                               Learner, manager, review and administration pages
frontend/public/brand/                      Official logo
scripts/                                   Private initialization, local MySQL acceptance, release checks
docs/                                      Operations, API, database, architecture, security, deployment and screenshots
THIRD_PARTY_NOTICES.md                      Third-party terms
compose.yaml                               Persistent database and health dependencies
```

## Install, initialize and configure

```sh
git clone https://github.com/zhuatech-han/zhuatech-trainflow.git
cd zhuatech-trainflow
python3 scripts/init-env.py
docker compose -p trainflow config --quiet
docker compose -p trainflow up -d --build --wait
```

Open [http://127.0.0.1:8106](http://127.0.0.1:8106), with [health](http://127.0.0.1:8106/actuator/health). Initial username is `admin`; privately read generated `ADMIN_PASSWORD` from `.env`. There is no public fixed password. Initialization generates three independent random passwords, file mode0600 and refuses existing-file overwrite. Account passwords require at least12characters with uppercase/lowercase/digits, and at most72UTF-8 bytes. Restart/environment changes do not reset existing accounts.

Empty initialization supplies headquarters, four roles, permissions, navigation, classifications, three parameters and administrator, without business courses or grades. Administrators create departments and separate training-manager, reviewer and learner accounts; these roles then operate the actual workflow.

| Variable | Purpose |
| --- | --- |
| `DATABASE_PASSWORD` | Required application database password |
| `MYSQL_ROOT_PASSWORD` | Required MySQL maintenance password |
| `ADMIN_PASSWORD` | Required empty-database administrator password |
| `WEB_PORT` / `BIND_ADDRESS` | Defaults8106 /127.0.0.1; choose a free test/project port |
| `COOKIE_SECURE` | False for local HTTP, true with properly configured HTTPS |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_CATALOG` | Direct backend startup connection/user/catalog; catalog must match actual database |

[.env.example](.env.example) lists names only. Compose fixes its database/user to `zhuatech_trainflow`/`trainflow`. Never commit `.env`. Database/backend host ports are not published.

For source development create an independent host-accessible MySQL8.4 database and restricted user, inject database variables/administrator password privately and run `mvn -f backend/pom.xml spring-boot:run` (8080). In `frontend`, run `npm ci` then `npm run dev`; Vite local5173 proxies to local8080. See [deployment](docs/deployment.md), without requiring the user's existing database.

## Database, upgrades, deployment and recovery

[Migration directory](backend/src/main/resources/db/migration/) contains V1 identity tables, V2 course/question/enrollment/exam/command/event tables and V3 course question-edit revision counter. Course code/edition, question position, exam sequence and command UUID uniqueness protect history. Foreign keys preserve people/version references. Attempts and events are append-only; changing a new edition cannot change earlier questions or scores. UTC timestamps and Shanghai business dates determine due/validity boundaries.

Before upgrade pause writes, back up the database/private configuration, rehearse restoration to an independent empty instance, inspect increasing migrations, build/start and verify health/login/full learning workflow. Preserve executed V1/V2/V3 checksums; add V4 rather than editing historical scripts. JPA is `validate`. Rollback involving schema migration needs matching backups and application images; old images do not automatically undo SQL.

```sh
docker compose -p trainflow exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction zhuatech_trainflow' > trainflow-backup.sql
docker compose -p trainflow restart mysql
docker compose -p trainflow up -d --wait mysql
docker compose -p trainflow restart backend frontend
docker compose -p trainflow up -d --wait
docker compose -p trainflow down
```

Keep backups private and compare original accounts, old/new editions, attempts, grades, practical review, events and redacted learner exports after restoration. `down` preserves the volume; `down -v` is only for a named disposable acceptance database. Deployment responsibilities include trusted HTTPS, secure cookies, controlled proxy/binding, database isolation, least privilege, backups, monitoring and separate safety/capacity review. Learning database TLS `sslMode=trust` does not verify CA/hostname; configure trusted certificates and `verify-full` as appropriate. Application/frontend use non-root users; backend runtime retains Java21/Maven tools. No production server/certificate or hosted service is provided.

## Tests and local acceptance

```sh
# Strong random test-only password, not committed:
TEST_ADMIN_PASSWORD="Aa9$(python3 -c 'import uuid; print(uuid.uuid4())')" mvn -f backend/pom.xml spotless:check test package
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose -p trainflow-check config --quiet
docker compose -p trainflow-check up -d --build --wait
# Only an independent fresh disposable local database:
python3 scripts/smoke.py
# Restart database, wait for health, then restart applications as documented above.
python3 scripts/smoke.py --verify
python3 scripts/release-check.py
git diff --check
```

Use matching `--base http://127.0.0.1:<port>` for a custom test port. The acceptance script creates marked `TEST` records and restricted ignored `.smoke-state.json`, refusing repeat creation. Do not run it on employee/business databases. Checks cover independent publication, assignment, a failed/successful exam, designated practical qualification, immutable old edition/results after new publication, permissions/redaction and persistence. H2 integration tests are not a substitute for fresh MySQL. Docker builds run all backend tests plus frontend checks/build. See [testing](docs/testing.md), [operations](docs/operations.md), [API](docs/api.md), [architecture](docs/architecture.md) and [security](docs/security.md).

## Security, errors and contributions

BCrypt12, 30-minute HttpOnly/SameSite=Strict sessions, CSRF and login-failure limits protect access. Disabling/password changes invalidate old sessions on subsequent checks. Admin APIs require permission and ALL scope. Course/evidence text is displayed without executable HTML; there are no arbitrary uploads. Credentials, grades/answer payloads and session secrets must not be publicly logged or committed.

| Issue | Check |
| --- | --- |
| Login fails | Original empty-database credentials/current account; restart does not change password |
| Service unhealthy | Scoped backend/MySQL logs, credentials, migrations and free port; do not publish raw logs |
| Version conflict | Refresh and review changes; retain key/full original payload only for exact retry |
| Assignment refused | Published edition, enabled learner with permission, department and independent reviewer |
| Task overdue | Cancel with a reason and create a new assignment, without backdating history |
| No practical action | Designated reviewer, independence, due date and written exam status |

Contributions should first describe a business issue, then submit a tested incremental change while preserving attribution and third-party terms. Use fictional/redacted data; never publish employee records, passwords, sessions or backups. Report vulnerabilities privately through the contacts below. Course legality, actual identity, practical judgment, backups and operation remain the deployer's responsibility. Do not automatically use these learning records for employment or legal-qualification decisions. No arbitrary production/certification fitness is promised.

## License and contact

Own code is publicly available for personal learning, research and non-commercial exchange under [LICENSE](LICENSE). Written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. is required for enterprise deployment, commercial delivery, paid deployment, SaaS, resale, commercial customization or paid services. This is not an OSI open-source license or MIT/Apache free-commercial license for the project's own code. Preserve third-party notices. Software is supplied as-is.

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**. Commercial authorization, customization, deployment and system integration:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
