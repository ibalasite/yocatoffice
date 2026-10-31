package com.sayyogames.yocat.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumer-Driven Contract: member-bc (consumer) -> wallet-bc (provider).
 *
 * Mirrors docs/blueprint/contracts/pact/member--wallet.json (2 interactions):
 *   1. MemberRegistered domain event provisions wallet + init ledger (Outbox async)
 *   2. MemberRegistered redelivery is idempotent (event_id dedup)
 *
 * Source: EDD §3.4 / §4.6.1 (member -> wallet, Customer/Supplier Event),
 *         API.md POST /auth/register, test-plan §3.8.3 SM-TEST-02-EVT-01.
 *
 * TDD role RED: the wallet-bc internal event endpoint (/internal/events/MemberRegistered)
 * is not yet implemented; provider-side verification against the real module turns GREEN
 * once contract-controllers wires the wallet event consumer. This class verifies the
 * consumer contract shape against the Pact mock provider (offline consumer-junit5).
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "wallet-bc", pactVersion = PactSpecVersion.V3)
class MemberWalletPactTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Pact(consumer = "member-bc", provider = "wallet-bc")
    public RequestResponsePact memberRegistered(PactDslWithProvider builder) {
        return builder
                .given("no wallet exists for the newly registered account")
                .uponReceiving("MemberRegistered domain event provisions wallet + init ledger (Outbox async)")
                .path("/internal/events/MemberRegistered")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringType("eventId", "e-7781")
                        .stringType("accountId", "1024")
                        .stringMatcher("email", "^[^@]+@[^@]+$", "boss@yocat.example"))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .booleanValue("ok", true)
                        .object("wallet")
                            .integerType("cash", 0L)
                            .integerType("gems", 0L)
                        .closeObject())
                .toPact();
    }

    @Pact(consumer = "member-bc", provider = "wallet-bc")
    public RequestResponsePact memberRegisteredIdempotent(PactDslWithProvider builder) {
        return builder
                .given("wallet already exists for account 1024 (event previously consumed)")
                .uponReceiving("MemberRegistered redelivery is idempotent (event_id dedup, wallet already provisioned)")
                .path("/internal/events/MemberRegistered")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringType("eventId", "e-7781")
                        .stringType("accountId", "1024")
                        .stringMatcher("email", "^[^@]+@[^@]+$", "boss@yocat.example"))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .booleanValue("ok", true)
                        .object("wallet")
                            .integerType("cash", 0L)
                            .integerType("gems", 0L)
                        .closeObject())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "memberRegistered")
    void memberRegistered_provisionsWalletWithZeroBalance(MockServer mockServer) throws Exception {
        HttpResponse<String> resp = postJson(
                mockServer.getUrl() + "/internal/events/MemberRegistered",
                "{\"eventId\":\"e-7781\",\"accountId\":\"1024\",\"email\":\"boss@yocat.example\"}");

        assertEquals(200, resp.statusCode());
        JsonNode body = MAPPER.readTree(resp.body());
        assertTrue(body.path("ok").asBoolean(), "ok must be true");
        assertEquals(0L, body.path("wallet").path("cash").asLong(), "init wallet cash=0");
        assertEquals(0L, body.path("wallet").path("gems").asLong(), "init wallet gems=0");
    }

    @Test
    @PactTestFor(pactMethod = "memberRegisteredIdempotent")
    void memberRegistered_redeliveryIsIdempotent(MockServer mockServer) throws Exception {
        HttpResponse<String> resp = postJson(
                mockServer.getUrl() + "/internal/events/MemberRegistered",
                "{\"eventId\":\"e-7781\",\"accountId\":\"1024\",\"email\":\"boss@yocat.example\"}");

        assertEquals(200, resp.statusCode(), "redelivery still acknowledged 200 (idempotent)");
        JsonNode body = MAPPER.readTree(resp.body());
        assertTrue(body.path("ok").asBoolean());
        assertEquals(0L, body.path("wallet").path("cash").asLong(), "no double-provision: cash stays 0");
        assertEquals(0L, body.path("wallet").path("gems").asLong(), "no double-provision: gems stays 0");
    }

    private static HttpResponse<String> postJson(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
