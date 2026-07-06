package com.iflowmonitor.iflowlab.app.runner;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Runner mode (SaaS R2, ADR-0001/0002): the app becomes a stateless execution
 * service — workspace endpoints disappear, and every request must carry the
 * runner token when one is configured.
 */
@QuarkusTest
@TestProfile(RunnerModeTest.RunnerProfile.class)
@Timeout(30)
class RunnerModeTest {

    public static class RunnerProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "iflowlab.runner-mode", "true",
                    "iflowlab.runner-token", "test-token");
        }
    }

    private static final String UPPERCASE =
            "import com.sap.gateway.ip.core.customdev.util.Message\n"
                    + "Message processData(Message message) {\n"
                    + "    message.setBody(message.getBody(String.class).toUpperCase())\n"
                    + "    return message\n"
                    + "}\n";

    @TestHTTPResource("/run/stream")
    URI streamUri;

    @Test
    void workspaceEndpoints_are404_inRunnerMode() {
        given().header("X-Runner-Token", "test-token").when().get("/workspace").then().statusCode(404);
        given().header("X-Runner-Token", "test-token")
                .when().get("/workspace/script?path=x.groovy").then().statusCode(404);
        given().header("X-Runner-Token", "test-token")
                .when().post("/case/run-all").then().statusCode(404);
    }

    @Test
    void run_withoutToken_is401_withToken_executes() {
        given().contentType("application/json")
                .body(Map.of("script", UPPERCASE, "body", "hi"))
                .when().post("/run")
                .then().statusCode(401);

        given().contentType("application/json")
                .header("X-Runner-Token", "test-token")
                .body(Map.of("script", UPPERCASE, "body", "hi"))
                .when().post("/run")
                .then().statusCode(200).body("status", is("OK")).body("body.inline", is("HI"));
    }

    @Test
    void lint_staysAvailable_withToken() {
        given().contentType("application/json")
                .header("X-Runner-Token", "test-token")
                .body(Map.of("script", "def f = new File('/x')"))
                .when().post("/lint")
                .then().statusCode(200);
    }

    @Test
    void webSocketUpgrade_withoutToken_isRejected() {
        URI wsUri = URI.create(streamUri.toString().replaceFirst("^http", "ws"));
        try {
            HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(wsUri, new WebSocket.Listener() {})
                    .get(10, TimeUnit.SECONDS);
            throw new AssertionError("upgrade without token should be rejected");
        } catch (Exception e) {
            assertThat(e).isInstanceOfAny(java.util.concurrent.ExecutionException.class, CompletionException.class);
        }
    }

    @Test
    void webSocketUpgrade_withToken_succeeds() throws Exception {
        URI wsUri = URI.create(streamUri.toString().replaceFirst("^http", "ws"));
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("X-Runner-Token", "test-token")
                .buildAsync(wsUri, new WebSocket.Listener() {})
                .get(10, TimeUnit.SECONDS);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }
}
