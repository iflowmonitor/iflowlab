package com.iflowmonitor.iflowlab.app;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RunResourceTest {

    private static final String UPPERCASE =
            "import com.sap.gateway.ip.core.customdev.util.Message\n"
                    + "Message processData(Message message) {\n"
                    + "    message.setBody(message.getBody(String.class).toUpperCase())\n"
                    + "    return message\n"
                    + "}\n";

    @Test
    void runEndpoint_executesScript_andReturnsEnvelope() {
        given().contentType("application/json")
                .body(Map.of("script", UPPERCASE, "body", "hello", "contentType", "text/plain"))
                .when()
                .post("/run")
                .then()
                .statusCode(200)
                .body("status", is("OK"))
                .body("body.type", is("TEXT"))
                .body("body.inline", is("HELLO"));
    }

    @Test
    void runEndpoint_reportsUncaughtException_withMappedLine() {
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "Message processData(Message message) {\n"
                        + "    throw new RuntimeException('kaboom')\n"
                        + "}\n";

        given().contentType("application/json")
                .body(Map.of("script", script, "body", "x"))
                .when()
                .post("/run")
                .then()
                .statusCode(200)
                .body("status", is("EXCEPTION"))
                .body("exception.message", is("kaboom"))
                .body("exception.mappedLine", is(3));
    }

    @Test
    void runEndpoint_timesOut_onRunawayScript() {
        String script =
                "import com.sap.gateway.ip.core.customdev.util.Message\n"
                        + "Message processData(Message message) {\n"
                        + "    while (true) {}\n"
                        + "    return message\n"
                        + "}\n";

        given().contentType("application/json")
                .body(Map.of("script", script, "body", "x", "timeoutMs", 500))
                .when()
                .post("/run")
                .then()
                .statusCode(200)
                .body("status", is("TIMEOUT"))
                .body("logs", notNullValue());
    }
}
