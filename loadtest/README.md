# Load tests (Gatling)

Two Gatling simulations (Java DSL) that run against a running backend. They are a separate
Maven project, so the backend build and CI do not depend on them. The results and the machine
they were measured on are in the main [README](../README.md#performance).

| Simulation | What it does |
|---|---|
| `smartteach.SubmitSimulation` | Two classes of 100 students on one assignment. Before the load, every student opens the test and saves 9 of 10 answers; then all 200 save the last answer and press "Submit" at the same moment. |
| `smartteach.LiveSimulation` | One teacher runs a 10-question live quiz for 50 players. Players join with the PIN, get every state over STOMP (WebSocket) and answer over REST; the teacher moves the game on. Also prints the push delay: from the server's `serverTime` in a state to the moment a player receives it. |

Each simulation registers a throwaway teacher (`loadtest-<uuid>@example.com`, random password)
and creates its own data, so it can run any number of times. Both fail if any request fails.

## Run

1. Start PostgreSQL (`docker compose up -d` in the repo root) and create an empty database
   for the test data, so it stays out of your dev data:

   ```bash
   docker compose exec db sh -c 'createdb -U "$POSTGRES_USER" loadtest'
   ```

2. Build and start the backend on port 8090 with rate limits off (every virtual user comes
   from one IP, so the per-IP limits would stop the test), from the repo root:

   ```bash
   cd backend && ./mvnw -B -q package -DskipTests && cd ..
   DB_URL=jdbc:postgresql://localhost:5432/loadtest RATE_LIMIT_ENABLED=false SERVER_PORT=8090 \
     java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
   ```

3. Run a simulation in another terminal:

   ```bash
   cd loadtest
   mvn -B gatling:test -Dgatling.simulationClass=smartteach.SubmitSimulation
   mvn -B gatling:test -Dgatling.simulationClass=smartteach.LiveSimulation
   ```

   The HTML report is written to `loadtest/target/gatling/<simulation>-<time>/index.html`.
   `-DbaseUrl=...` points the tests at another backend; `-Dorigin=...` must then be one of its
   `CORS_ALLOWED_ORIGINS`, or the WebSocket handshake is refused.

4. Clean up: stop the backend, then
   `docker compose exec db sh -c 'dropdb -U "$POSTGRES_USER" loadtest'`.

## What the tests found

With Tomcat's default `accept-count` of 100, 5 of 8 runs of `SubmitSimulation` on a freshly
started backend had failed requests (up to 9.75%): `java.net.ConnectException: Connection
refused`. The 200 connections arrive in the same millisecond; while the new JVM is still slow,
the queue of connections waiting to be accepted fills up and the OS refuses the rest. The
backend now sets `server.tomcat.accept-count: 1000`; with it, 8 of 8 runs had no failed request.
A bigger database pool (30 instead of Hikari's default 10) did not change the response times,
so the pool stays at its default.
