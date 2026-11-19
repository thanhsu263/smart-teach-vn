# smart-teach-vn

SmartTeach helps Vietnamese high-school teachers manage classes, build quizzes and give
assignments that students take with a personal access code. Spring Boot backend +
React frontend.

> Work in progress — this README will be expanded as features land.

## Tech stack

- **Backend:** Java 26, Spring Boot 4.1 (Web MVC, Security, OAuth2 Resource Server for JWT,
  Data JPA, Validation, Actuator), Flyway, MapStruct, springdoc-openapi
- **Database:** Microsoft SQL Server 2022
- **Tests:** JUnit 5, Mockito, Testcontainers (real SQL Server), JaCoCo
- **Frontend:** React + Vite + TypeScript, React Router, TanStack Query, Tailwind CSS,
  react-hook-form + zod, axios

## Run locally

Requirements: JDK 26, Node 24, Docker.

```bash
cp .env.example .env              # then change the passwords and JWT_SECRET
docker compose up -d              # SQL Server on localhost:14333 + creates the database

cd backend
./mvnw spring-boot:run            # Flyway applies migrations on startup
curl http://localhost:8080/actuator/health

cd ../frontend
npm install
npm run dev                       # http://localhost:5173
```

The backend reads `../.env` automatically, so no extra environment setup is needed when
running from `backend/`. In dev the Vite server proxies `/api` to the backend port from
`.env` (`SERVER_PORT`), so the browser only talks to one origin.

API docs (Swagger UI): http://localhost:8080/swagger-ui.html

### Environment variables

| Variable | Used by | Description |
|---|---|---|
| `MSSQL_SA_PASSWORD` | docker compose | SQL Server `sa` password |
| `DB_PORT` | both | Host port of SQL Server (default `14333`) |
| `DB_URL` | backend | Full JDBC URL; overrides the local default (cloud deploys) |
| `DB_USERNAME`, `DB_PASSWORD` | backend | Database credentials |
| `SERVER_PORT` | backend, Vite proxy | Backend port (default `8080`) |
| `JWT_SECRET` | backend | HS256 key, **at least 32 bytes** (`openssl rand -base64 48`); the app refuses to start without it |
| `CORS_ALLOWED_ORIGINS` | backend | Comma-separated frontend origins (default `http://localhost:5173`) |
| `AUTH_COOKIE_SECURE` | backend | `true` in production (HTTPS) |
| `AUTH_COOKIE_SAME_SITE` | backend | `Lax` by default; `None` when frontend and backend are on different sites |
| `VITE_API_URL` | frontend build | Backend origin in production; leave empty in dev |

## Authentication

Teachers sign up and sign in with email + password (BCrypt). Public sign-up always creates a
`TEACHER`; `ADMIN` accounts are created directly in the database.

| Endpoint | Description |
|---|---|
| `POST /api/auth/register` | Create an account and sign in (201) |
| `POST /api/auth/login` | Sign in (401 with the same message for unknown email and wrong password) |
| `POST /api/auth/refresh` | Rotate the refresh token, return a new access token |
| `POST /api/auth/logout` | Revoke the refresh token and clear the cookie (204) |
| `GET /api/me` | Current user (needs `Authorization: Bearer <access token>`) |

How it works:

- **Access token:** a JWT signed with HS256, valid for 15 minutes, returned in the response
  body. `sub` is the user id, the `role` claim becomes `ROLE_TEACHER` / `ROLE_ADMIN`. The
  frontend keeps it in memory only.
- **Refresh token:** 32 random bytes sent as an `HttpOnly` cookie limited to `/api/auth`,
  valid for 14 days. Only its SHA-256 hash is stored in `refresh_tokens`.
- **Rotation and reuse detection:** every refresh revokes the presented token and issues a
  new one in the same *family*. If a token that was already revoked is presented again, it
  was stolen or replayed, so the whole family is revoked and the user must sign in again.
  The revoke is a conditional `UPDATE … WHERE revoked_at IS NULL`, so two parallel refreshes
  with the same token cannot both succeed.
- **Frontend:** an axios interceptor catches `401`, calls `/api/auth/refresh` once (parallel
  requests share the same call) and retries the original request. On page load the session
  is restored from the refresh cookie.
- **Errors:** every error is an RFC 9457 `ProblemDetail`; validation errors include an
  `errors` map (field → message).

## Tests

```bash
cd backend && ./mvnw verify       # needs Docker running (Testcontainers)
cd frontend && npm run lint && npm run build
```

Integration tests start one SQL Server container and share a single Spring context. Auth tests
cover wrong password, expired and tampered tokens, refresh-token rotation and reuse, logout,
validation errors and role-protected endpoints (401 / 403 / 200).

The JaCoCo report is written to `backend/target/site/jacoco/index.html`.
