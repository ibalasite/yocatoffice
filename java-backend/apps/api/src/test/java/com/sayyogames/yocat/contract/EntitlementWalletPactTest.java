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
 * Consumer-Driven Contract: entitlement-bc (consumer) -> wallet-bc (provider).
 *
 * Mirrors docs/blueprint/contracts/pact/entitlement--wallet.json (2 interactions):
 *   1. WalletService.debit() atomic debit within buy transaction (happy path, 200)
 *   2. WalletService.debit() rejects when balance < amount (402, transaction rolls back)
 *
 * Source: EDD §3.4 (entitlement -> wallet, synchronous Customer/Supplier Public API),
 *         API.md POST /entitlements/buy, test-plan §3.8.3 SM-TEST-02-API-01.
 *
 * TDD role RED: wallet-bc /internal/wallet/debit public API is not yet implemented;
 * provider verification turns GREEN with contract-controllers.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "wallet-bc", pactVersion = PactSpecVersion.V3)
class EntitlementWalletPactTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Pact(consumer = "entitlement-bc", provider = "wallet-bc")
    public RequestResponsePact debitSucceeds(PactDslWithProvider builder) {
        return builder
                .given("account 1024 exists with wallet gems >= 120")
                .uponReceiving("WalletService.debit() atomic debit within buy transaction (happy path)")
                .path("/internal/wallet/debit")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringType("accountId", "1024")
                        .stringMatcher("currency", "cash|gems", "gems")
                        .integerType("amount", 120L)
                        .stringType("reason", "buy_cosmetic")
                        .object("meta")
                            .stringType("itemId", "suit_gold")
                        .closeObject())
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .booleanValue("ok", true)
                        .stringValue("currency", "gems")
                        .integerType("balance", 210L)
                        .object("wallet")
                            .integerType("cash", 18750L)
                            .integerType("gems", 210L)
                        .closeObject())
                .toPact();
    }

    @Pact(consumer = "entitlement-bc", provider = "wallet-bc")
    public RequestResponsePact debitInsufficientBalance(PactDslWithProvider builder) {
        return builder
                .given("account 2048 exists with wallet gems = 50")
                .uponReceiving("WalletService.debit() rejects when balance < amount (402, transaction rolls back)")
                .path("/internal/wallet/debit")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonBody()
                        .stringType("accountId", "2048")
                        .stringMatcher("currency", "cash|gems", "gems")
                        .integerType("amount", 120L)
                        .stringType("reason", "buy_cosmetic"))
                .willRespondWith()
                .status(402)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringType("error", "餘額不足")
                        .object("wallet")
                            .integerType("cash", 0L)
                            .integerType("gems", 50L)
                        .closeObject())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "debitSucceeds")
    void debit_happyPathReturns200AndNewBalance(MockServer mockServer) throws Exception {
        HttpResponse<String> resp = postJson(
                mockServer.getUrl() + "/internal/wallet/debit",
                "{\"accountId\":\"1024\",\"currency\":\"gems\",\"amount\":120,"
                        + "\"reason\":\"buy_cosmetic\",\"meta\":{\"itemId\":\"suit_gold\"}}");

        assertEquals(200, resp.statusCode());
        JsonNode body = MAPPER.readTree(resp.body());
        assertTrue(body.path("ok").asBoolean());
        assertEquals("gems", body.path("currency").asText());
        assertEquals(210L, body.path("balance").asLong(), "balance after atomic debit");
        assertEquals(210L, body.path("wallet").path("gems").asLong());
    }

    @Test
    @PactTestFor(pactMethod = "debitInsufficientBalance")
    void debit_insufficientBalanceReturns402AndRollsBack(MockServer mockServer) throws Exception {
        HttpResponse<String> resp = postJson(
                mockServer.getUrl() + "/internal/wallet/debit",
                "{\"accountId\":\"2048\",\"currency\":\"gems\",\"amount\":120,\"reason\":\"buy_cosmetic\"}");

        assertEquals(402, resp.statusCode(), "PAYMENT_REQUIRED on insufficient balance");
        JsonNode body = MAPPER.readTree(resp.body());
        assertEquals("餘額不足", body.path("error").asText());
        assertEquals(50L, body.path("wallet").path("gems").asLong(),
                "rollback: balance unchanged (no half-debit)");
    }

    private static HttpResponse<String> postJson(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
