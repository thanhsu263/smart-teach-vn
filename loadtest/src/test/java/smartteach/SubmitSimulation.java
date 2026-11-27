package smartteach;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

/**
 * 200 students (two classes of 100 on the same test) press "Submit" at the same moment: each one
 * saves the last answer, then submits, which grades the attempt on the server.
 */
public class SubmitSimulation extends Simulation {

    private static final int CLASSES = 2;
    private static final int STUDENTS_PER_CLASS = 100;

    private final ConcurrentLinkedQueue<Map<String, Object>> attempts = new ConcurrentLinkedQueue<>();

    private final HttpProtocolBuilder protocol = http.baseUrl(Api.BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json");

    private final Iterator<Map<String, Object>> feeder = new Iterator<>() {
        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public Map<String, Object> next() {
            return attempts.remove();
        }
    };

    private final ScenarioBuilder submit = scenario("Submit at the same time")
            .feed(feeder)
            .exec(http("Save last answer")
                    .put("/api/public/attempts/#{attemptId}/answers/" + (Api.QUESTIONS - 1))
                    .header("X-Attempt-Token", "#{token}")
                    .body(StringBody("{\"answer\": 0}"))
                    .check(status().is(200)))
            .exec(http("Submit")
                    .post("/api/public/attempts/#{attemptId}/submit")
                    .header("X-Attempt-Token", "#{token}")
                    .check(status().is(200), jsonPath("$.score").exists()));

    {
        setUp(submit.injectOpen(atOnceUsers(CLASSES * STUDENTS_PER_CLASS)))
                .protocols(protocol)
                .assertions(global().failedRequests().percent().is(0.0));
    }

    /** Every student opens the test and answers 9 of the 10 questions before the load starts. */
    @Override
    public void before() {
        Api api = new Api();
        long quizId = api.createQuiz();
        List<Thread> threads = new ArrayList<>();
        for (int c = 0; c < CLASSES; c++) {
            Api.NewClass newClass = api.createClass("Lớp " + (c + 1), STUDENTS_PER_CLASS);
            String publicCode = api.publishAssignment(newClass.id(), quizId);
            // 10 students at a time, so the preparation takes seconds, not minutes.
            IntStream.range(0, 10).forEach(slice -> threads.add(Thread.ofVirtual().start(() -> {
                for (int i = slice; i < newClass.accessCodes().size(); i += 10) {
                    String[] attempt = api.startAndAnswerAllButLast(publicCode, newClass.accessCodes().get(i));
                    attempts.add(Map.of("attemptId", attempt[0], "token", attempt[1]));
                }
            })));
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        if (attempts.size() != CLASSES * STUDENTS_PER_CLASS) {
            throw new IllegalStateException("Prepared " + attempts.size() + " attempts");
        }
    }
}
