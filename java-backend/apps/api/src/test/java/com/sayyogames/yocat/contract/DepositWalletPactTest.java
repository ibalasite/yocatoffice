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
 * Consumer-Driven Contract: deposit-bc (consumer) -> wallet-bc (provider).
 *
 * Mirrors docs/blueprint/contracts/pact/deposit--wallet.json (2 interactions):
 *   1. DepositConfirmed event credits wallet + writes topup ledger (Outbox async, orderId dedup)
 *   2. DepositConfirmed redelivery with same orderId credits only once (idempotent)
 *
 * Source: EDD §3.4 / §4.6.1 (deposit -> wallet, Customer/Supplier Event),
 *         API.md POST /deposit/checkout, test-plan §3.8.3 SM-TEST-02-EVT-02.
 *
 * TDD role RED: wallet-bc /internal/events/DepositConfirmed consumer is not yet
 * implemented; provider verification turns GREEN with contract-controllers.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "wallet-bc", pactVersion = PactSpecVersion.V3)
class DepositWalletPactTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static final String ORDER_ID = "7d793037-a076-4c05-9a70-b7e8b2e3c4f5";

    @Pact(consumer = "deposit-bc", provider = "wallet-bc")
    public RequestResponsePact depositConfirmedCredits(PactDslWithProvider builder) {
        return builder
                .given("account 1024 has a wallet and order 7d793037 not yet credited")
                .uponReceiving("DepositConfirmed event credits wallet + writes topup ledger (Outbox async, orderId dedup)")
                .path("/internal/events/DepositConfirmed")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringType("eventId", "e-9001")
                        .stringType("accountId", "1024")
                        .stringMatcher("orderId", "[0-9a-fA-F-]{36}", ORDER_ID)
                        .stringMatcher("currency", "cash|gems", "gems")
                        .integerType("amount", 700L))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .booleanValue("ok", true)
                        .stringValue("currency", "gems")
                        .integerType("balance", 1030L)
                        .object("wallet")
                            .integerType("cash", 18750L)
                            .integerType("gems", 1030L)
                        .closeObject())
                .toPact();
    }

    @Pact(consumer = "deposit-bc", provider = "wallet-bc")
    public RequestResponsePact depositConfirmedIdempotent(PactDslWithProvider builder) {
        return builder
                .given("order 7d793037 already credited to account 1024")
                .uponReceiving("DepositConfirmed redelivery with same orderId credits only once (idempotent)")
                .path("/internal/events/DepositConfirmed")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringType("eventId", "e-9001")
                        .stringType("accountId", "1024")
                        .stringMatcher("orderId", "[0-9a-fA-F-]{36}", ORDER_ID)
                        .stringMatcher("currency", "cash|gems", "gems")
                        .integerType("amount", 700L))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .booleanValue("ok", true)
                        .stringValue("currency", "gems")
                        .integerType("balance", 1030L)
                        .object("wallet")
                            .integerType("cash", 18750L)
                            .integerType("gems", 1030L)
                        .closeObject())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "depositConfirmedCredits")
    void depositConfirmed_creditsWalletOnce(MockServer mockServer) throws Exception {
        HttpResponse<String> resp = postJson(
                mockServer.getUrl() + "/internal/events/DepositConfirmed",
                "{\"eventId\":\"e-9001\",\"accountId\":\"1024\",\"orderId\":\"" + ORDER_ID
                        + "\",\"currency\":\"gems\",\"amount\":700}");

        assertEquals(200, resp.statusCode());
        JsonNode body = MAPPER.readTree(resp.body());
        assertTrue(body.path("ok").asBoolean());
        assertEquals("gems", body.path("currency").asText());
        assertEquals(1030L, body.path("balance").asLong(), "credited balance");
        assertEquals(1030L, body.path("wallet").path("gems").asLong());
    }

    @Test
    @PactTestFor(pactMethod = "depositConfirmedIdempotent")
    void depositConfirmed_redeliverySameOrderIdCreditsOnce(MockServer mockServer) throws Exception {
        HttpResponse<String> resp = postJson(
                mockServer.getUrl() + "/internal/events/DepositConfirmed",
                "{\"eventId\":\"e-9001\",\"accountId\":\"1024\",\"orderId\":\"" + ORDER_ID
                        + "\",\"currency\":\"gems\",\"amount\":700}");

        assertEquals(200, resp.statusCode());
        JsonNode body = MAPPER.readTree(resp.body());
        assertTrue(body.path("ok").asBoolean());
        assertEquals(1030L, body.path("balance").asLong(),
                "orderId dedup: balance unchanged on redelivery (credited once)");
    }

    private static HttpResponse<String> postJson(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
