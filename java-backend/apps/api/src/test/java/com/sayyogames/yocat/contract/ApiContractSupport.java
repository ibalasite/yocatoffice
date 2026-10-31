package com.sayyogames.yocat.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shared HTTP helper for the API contract tests against docs/API.md §3 Endpoints
 * and docs/blueprint/contracts/openapi.yaml.
 *
 * These are black-box HTTP contract tests: each test fires a real request at the
 * running provider ({@code -Dcontract.baseUrl}, default http://localhost:8080) and
 * asserts the documented status code + response JSON shape per API.md.
 *
 * TDD role RED: no provider is running yet (controllers are implemented by the
 * downstream contract-controllers GREEN sub-step), so these *ContractIT tests fail
 * at runtime until the API layer exists. They encode the executable contract now.
 * The RED bar for this sub-step is {@code mvn -o test-compile} (exit 0); these are
 * excluded from the unit Surefire run by the {@code *IT} naming convention.
 */
abstract class ApiContractSupport {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected static final String BASE_URL =
            System.getProperty("contract.baseUrl", "http://localhost:8080");

    protected static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Default session token value; a real token is injected via {@code -Dcontract.token} by the verification harness. */
    protected static final String BEARER = System.getProperty("contract.token", "test-session-token");

    protected HttpResponse<String> get(String path) throws Exception {
        return send(builder(path).GET());
    }

    protected HttpResponse<String> getAuth(String path) throws Exception {
        return send(builder(path).header("Authorization", "Bearer " + BEARER).GET());
    }

    protected HttpResponse<String> postJson(String path, String body) throws Exception {
        return send(builder(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    protected HttpResponse<String> postJsonAuth(String path, String body, String... extraHeaders) throws Exception {
        HttpRequest.Builder b = builder(path)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + BEARER);
        for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
            b.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        return send(b.POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    protected HttpResponse<String> putJsonAuth(String path, String body) throws Exception {
        return send(builder(path)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + BEARER)
                .PUT(HttpRequest.BodyPublishers.ofString(body)));
    }

    protected HttpResponse<String> putJson(String path, String body) throws Exception {
        return send(builder(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(10));
    }

    private HttpResponse<String> send(HttpRequest.Builder b) throws Exception {
        return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected JsonNode json(HttpResponse<String> resp) throws Exception {
        return MAPPER.readTree(resp.body());
    }

    /** API.md §5.2 Error Code Registry: every error response body carries an {@code error} field. */
    protected void assertErrorShape(HttpResponse<String> resp, int expectedStatus) throws Exception {
        Assertions.assertEquals(expectedStatus, resp.statusCode(),
                "expected documented error status " + expectedStatus);
        JsonNode body = json(resp);
        Assertions.assertTrue(body.hasNonNull("error"),
                "API.md §5.2: error response must contain an 'error' field");
    }
}
