package com.iflowmonitor.iflowlab.app;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class WorkspaceResourceTest {

    @Test
    void info_carriesRecentsList() {
        given().when().get("/workspace").then().statusCode(200).body("recents", notNullValue());
    }

    @Test
    void open_nonExistentDir_is400() {
        given().contentType("application/json")
                .body(Map.of("path", "C:/no/such/workspace/should/exist"))
                .when()
                .post("/workspace/open")
                .then()
                .statusCode(400);
    }
}
