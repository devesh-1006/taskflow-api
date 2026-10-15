# TaskFlow API

TaskFlow is a Java 21 REST API for managing each user's personal tasks. It uses Spring Boot, Spring Security with JWT, Spring Data JPA, PostgreSQL, Flyway, and Bean Validation. Swagger UI is an API testing tool; this repository does not include a separate end-user web interface.

## Requirements

- JDK 21
- Maven 3.9 or newer
- Docker Desktop with Docker Compose, or a PostgreSQL 17 database

## Run locally with PostgreSQL

1. Copy `.env.example` to `.env`, replace the example database password, and generate a JWT key (the example below uses JShell). Never commit `.env`.

   ```powershell
   Copy-Item .env.example .env
   jshell
   ```

   In JShell, generate and copy the output into `JWT_SECRET_BASE64` in `.env`:

   ```java
   import java.security.SecureRandom;
   import java.util.Base64;
   byte[] key = new byte[32];
   new SecureRandom().nextBytes(key);
   System.out.println(Base64.getEncoder().encodeToString(key));
   /exit
   ```

2. Start PostgreSQL. Docker Compose reads the database username/password from `.env`:

   ```powershell
   docker compose up -d --wait postgres
   ```

3. Set the same credentials and JWT key in the current PowerShell process. Maven does not load `.env` automatically:

   ```powershell
   $env:DB_URL = "jdbc:postgresql://localhost:5432/taskflow"
   $env:DB_USERNAME = "taskflow"
   $env:DB_PASSWORD = "<the-password-from-.env>"
   $env:JWT_SECRET_BASE64 = "<the-generated-base64-key>"
   mvn spring-boot:run
   ```

   Flyway applies the versioned SQL migrations on startup. Hibernate uses `ddl-auto: validate`, so it checks the schema rather than silently creating or changing production tables. Confirm the startup log reports a successful migration and check `http://localhost:8080/actuator/health`.

4. Stop the application with Ctrl+C. Stop PostgreSQL without deleting its persisted volume:

   ```powershell
   docker compose stop postgres
   ```

Do not use `docker compose down -v` unless you intentionally want to delete the local database volume and its data.

## Run tests

```powershell
mvn clean verify
```

The automated tests use an in-memory H2 database configured in `src/test/resources/application-test.yml`. Hibernate creates a temporary test schema and Flyway is disabled in that profile. This makes the test suite independent of a local PostgreSQL installation, but it does **not** verify that PostgreSQL accepts the Flyway migration. To exercise the migration with PostgreSQL, follow the local run steps above and inspect Flyway's startup result. A PostgreSQL-backed test suite should be added when a PostgreSQL test service (or Docker/Testcontainers) is available.

## API

Public authentication endpoints:

- `POST /api/auth/register` creates an account and returns an access token.
- `POST /api/auth/login` checks credentials and returns an access token.

All task endpoints require an `Authorization: Bearer <access-token>` header:

- `POST /api/tasks` creates a task for the signed-in user.
- `GET /api/tasks` lists only that user's tasks.
- `GET /api/tasks/{id}` gets one of that user's tasks.
- `PUT /api/tasks/{id}` replaces the editable fields of one of that user's tasks.
- `DELETE /api/tasks/{id}` deletes one of that user's tasks.

The list endpoint accepts optional `status`, `priority`, `dueAfter`, `dueBefore`, and `search` filters. It also supports pagination and sorting, for example `?page=0&size=20&sort=createdAt,desc`. The default page size is 20, requested page sizes are limited to 100, and unsupported sort fields return a client error. An omitted or non-positive page size uses Spring's default page size.

Start the API and open `http://localhost:8080/swagger-ui.html` to explore the requests. Register or log in, then use the Swagger UI **Authorize** control and enter `Bearer ` followed by the returned token before trying task operations. Health is available at `/actuator/health`; OpenAPI JSON is at `/v3/api-docs`.

## Security and configuration

- Passwords are stored as BCrypt hashes; plaintext passwords are not persisted.
- JWT signing requires `JWT_SECRET_BASE64`. The decoded key must be at least 32 bytes. Tokens expire after 15 minutes by default; `JWT_EXPIRATION` accepts a Java duration such as `PT15M`.
- Supply `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` through environment variables. The default URL is for local PostgreSQL, but the password has no source-code default.
- Do not commit real credentials or signing keys. `.env` is excluded by `.gitignore`; `.env.example` contains placeholders only.
- A task that belongs to a different user is treated as not found, so the API does not disclose another user's task IDs.

## Portfolio deployment considerations

The repository does not currently include a Dockerfile or deployment manifest, and no deployment has been performed. A JVM app can be deployed to a container-based web service such as Render, but a Dockerfile or another supported Java build/runtime configuration must first be added. Set the deployment platform's port variable (`PORT`) and keep `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and a newly generated `JWT_SECRET_BASE64` in its secret/environment settings. Use a managed PostgreSQL service with TLS, apply Flyway migrations at startup, and check `/actuator/health`.

One possible low-cost portfolio combination is a Render free web service plus a Neon free PostgreSQL project. Render's official free-plan documentation says free web services spin down after 15 minutes without inbound traffic, use an ephemeral filesystem, and have 750 included instance hours per workspace per month. Neon currently lists 1 GB of storage per free project, 100 CU-hours per project, and scale-to-zero after five minutes. These quotas and terms can change; check the provider pages before deploying:

- [Render free service limitations](https://render.com/docs/free)
- [Render Docker deployments](https://render.com/docs/docker)
- [Neon plans and quotas](https://neon.com/docs/introduction/plans)
- [Neon pricing](https://neon.com/pricing)

Do not use Render's free PostgreSQL database for durable portfolio data: its current free database offering expires after 30 days and has no backups. Free tiers are suitable for a demo, not an availability or recovery guarantee. Keep a copy/export of data you care about, monitor quotas and provider terms, and never store database files on the web-service filesystem. Authentication rate limiting and a production backup/restore plan are also not implemented in this project and should be considered before exposing it to real users.

## Current verification coverage

`TaskFlowApiIntegrationTest` covers registration/login, password hashing, task creation and listing, CRUD, owner isolation for list/read/update/delete, token failures, common validation/auth errors, filters, sorting, pagination, and page-size bounds. Unit tests cover owner-scoped task lookup and JWT expiry/signature validation. The tests run against H2; PostgreSQL migration compatibility remains a separate, unverified check unless the migration is run against an actual PostgreSQL server.
