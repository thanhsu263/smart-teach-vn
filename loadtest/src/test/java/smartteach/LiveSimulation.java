package smartteach;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.asLongAs;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.pause;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.regex;
import static io.gatling.javaapi.core.CoreDsl.repeat;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static io.gatling.javaapi.http.HttpDsl.ws;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One teacher runs a 10-question live quiz for 50 players. Players get every state change over
 * STOMP (WebSocket) and answer over REST; the host moves the game on over REST.
 *
 * <p>Besides Gatling's request times, this measures the push delay: the time from the state the
 * server sent (its {@code serverTime}) to the moment a player receives it.
 */
public class LiveSimulation extends Simulation {

    private static final int PLAYERS = 50;
    /** STOMP frames end with a NUL byte; a lone newline is a heart-beat. */
    private static final String NUL = "\u0000";
    private static final String HEARTBEAT = "\n";

    private static final List<Long> pushDelaysMillis = new ArrayList<>();
    private static final AtomicInteger nicknames = new AtomicInteger();

    private String pin;
    private String hostAuth;

    private final HttpProtocolBuilder protocol = http.baseUrl(Api.BASE_URL)
            .wsBaseUrl(Api.BASE_URL.replaceFirst("^http", "ws"))
            .contentTypeHeader("application/json")
            .acceptHeader("application/json");

    private final ScenarioBuilder players = scenario("Player")
            .exec(session -> session.set("pin", pin).set("nickname", "HS " + nicknames.incrementAndGet()))
            .exec(ws("Open WebSocket").connect("/ws").header("Origin", Api.ORIGIN)
                    .onConnected(exec(ws("STOMP CONNECT")
                            .sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n" + NUL)
                            .await(5).on(ws.checkTextMessage("CONNECTED").check(regex("^CONNECTED"))))))
            .exec(ws("Subscribe").sendText("SUBSCRIBE\nid:0\ndestination:/topic/live/#{pin}\n\n" + NUL))
            // Join after subscribing: once the host sees 50 players, every one of them is listening.
            .exec(http("Join").post("/api/public/live/#{pin}/players")
                    .body(StringBody("{\"nickname\": \"#{nickname}\"}"))
                    .check(status().is(201), jsonPath("$.token").saveAs("token")))
            .repeat(Api.QUESTIONS, "q").on(
                    // The heart-beat is only there to arm the check; the awaited frame is the push
                    // the server sends when the host opens question q.
                    exec(ws("Wait for question").sendText(HEARTBEAT)
                            .await(60).on(ws.checkTextMessage("Question opened")
                                    .matching(regex("\"status\":\"question\".*\"question\":\\{\"position\":#{q},"))
                                    .check(regex("\"serverTime\":\"([^\"]+)\"").saveAs("serverTime"))))
                    .exec(session -> {
                        long delay = Duration.between(Instant.parse(session.getString("serverTime")), Instant.now())
                                .toMillis();
                        synchronized (pushDelaysMillis) {
                            pushDelaysMillis.add(delay);
                        }
                        return session;
                    })
                    .exec(http("Answer").post("/api/public/live/#{pin}/answers")
                            .header("X-Player-Token", "#{token}")
                            .body(StringBody(session -> "{\"position\": %d, \"answer\": %d}"
                                    .formatted(session.getInt("q"), ThreadLocalRandom.current().nextInt(2))))
                            .check(status().is(204))))
            .exec(ws("Close WebSocket").close());

    private final ScenarioBuilder host = scenario("Host")
            .exec(session -> session.set("pin", pin).set("auth", hostAuth).set("joined", 0))
            .exec(asLongAs(session -> session.getInt("joined") < PLAYERS).on(
                    pause(Duration.ofMillis(500)),
                    exec(http("Host: lobby").get("/api/live/#{pin}").header("Authorization", "#{auth}")
                            .check(jsonPath("$.players[*]").count().saveAs("joined")))))
            .pause(1)
            .repeat(Api.QUESTIONS).on(
                    exec(http("Host: open question").post("/api/live/#{pin}/next").header("Authorization", "#{auth}")
                            .check(jsonPath("$.status").is("question").saveAs("status")))
                    // The room shows the answer by itself once every player has answered.
                    .asLongAs(session -> !"reveal".equals(session.getString("status"))).on(
                            pause(Duration.ofMillis(200)),
                            exec(http("Host: wait for reveal").get("/api/live/#{pin}").header("Authorization", "#{auth}")
                                    .check(jsonPath("$.status").saveAs("status"))))
                    .exec(http("Host: answer shown").get("/api/live/#{pin}").header("Authorization", "#{auth}")
                            .check(jsonPath("$.status").is("reveal"), jsonPath("$.answeredCount").ofInt().is(PLAYERS)))
                    .pause(1))
            .exec(http("Host: finish").post("/api/live/#{pin}/next").header("Authorization", "#{auth}")
                    .check(jsonPath("$.status").is("ended")));

    {
        setUp(
                players.injectOpen(rampUsers(PLAYERS).during(Duration.ofSeconds(5))),
                host.injectOpen(atOnceUsers(1)))
                .protocols(protocol)
                .assertions(global().failedRequests().percent().is(0.0));
    }

    @Override
    public void before() {
        Api api = new Api();
        hostAuth = api.auth();
        pin = api.openLiveRoom(api.createQuiz());
    }

    @Override
    public void after() {
        List<Long> sorted = pushDelaysMillis.stream().sorted().toList();
        if (sorted.isEmpty()) {
            return;
        }
        System.out.printf("Push delay over %d messages: p50 %d ms, p95 %d ms, p99 %d ms, max %d ms%n",
                sorted.size(), percentile(sorted, 50), percentile(sorted, 95), percentile(sorted, 99),
                sorted.getLast());
    }

    private static long percentile(List<Long> sorted, int p) {
        return sorted.get((int) Math.ceil(p / 100.0 * sorted.size()) - 1);
    }
}
