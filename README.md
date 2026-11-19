# smart-teach-vn

SmartTeach helps Vietnamese high-school teachers manage classes, build quizzes and give
assignments that students take with a personal access code. Spring Boot backend +
React frontend.

> Work in progress — this README will be expanded as features land.

## Tech stack

- **Backend:** Java 26, Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator), Flyway
- **Database:** Microsoft SQL Server 2022
- **Tests:** JUnit 5, Testcontainers (real SQL Server), JaCoCo
- **Frontend:** React + Vite + TypeScript

## Run locally

Requirements: JDK 26, Node 24, Docker.

```bash
cp .env.example .env              # then change the passwords
docker compose up -d              # SQL Server on localhost:14333 + creates the database

cd backend
./mvnw spring-boot:run            # Flyway applies migrations on startup
curl http://localhost:8080/actuator/health

cd ../frontend
npm install
npm run dev                       # http://localhost:5173
```

The backend reads `../.env` automatically, so no extra environment setup is needed when
running from `backend/`.

## Tests

```bash
cd backend && ./mvnw verify       # needs Docker running (Testcontainers)
cd frontend && npm run lint && npm run build
```

The JaCoCo report is written to `backend/target/site/jacoco/index.html`.
