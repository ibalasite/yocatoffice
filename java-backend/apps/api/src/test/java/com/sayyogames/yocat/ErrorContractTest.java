package com.sayyogames.yocat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sayyogames.yocat.entitlement.application.AlreadyOwnedException;
import com.sayyogames.yocat.entitlement.presentation.EntitlementExceptionAdvice;
import com.sayyogames.yocat.member.application.EmailAlreadyExistsException;
import com.sayyogames.yocat.member.presentation.MemberExceptionAdvice;
import com.sayyogames.yocat.platform.web.IdempotencyConflictException;
import com.sayyogames.yocat.platform.web.UnauthorizedException;
import com.sayyogames.yocat.shared.InvalidAmountException;
import com.sayyogames.yocat.wallet.domain.InsufficientBalanceException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Error-code contract test (O-10) — asserts the whole API.md §5.2 Error Code Registry is realised
 * exactly once, with the documented HTTP status + zh-TW / documented message, by the composed exception
 * advices (the central {@link ApiExceptionHandler} plus the per-BC {@link MemberExceptionAdvice} /
 * {@link EntitlementExceptionAdvice}). Pure unit test: the advice methods are invoked directly, so it runs
 * in the Surefire unit pass (no Spring context, no Testcontainers). Runtime proof of 429 RATE_LIMITED and
 * of the error path over real HTTP lives in the Cucumber {@code RunBddContractIT} suite.
 *
 * <p>Registry coverage (API §5.2): 400 VALIDATION_ERROR / 401 UNAUTHORIZED / 402 PAYMENT_REQUIRED /
 * 404 NOT_FOUND / 409 CONFLICT / 500 INTERNAL_ERROR are owned by these advices and asserted here.
 * 429 RATE_LIMITED is produced by the BearerTokenFilter/RateLimiter; 413 PAYLOAD_TOO_LARGE and 503
 * SERVICE_UNAVAILABLE are servlet-container / lifecycle concerns — see each endpoint's §3 error table.</p>
 */
class ErrorContractTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private static String errorOf(ResponseEntity<Map<String, Object>> resp) {
        return String.valueOf(resp.getBody().get("error"));
    }

    @Test
    @DisplayName("400 VALIDATION_ERROR: InvalidAmountException → 400, message carried")
    void invalidAmountIs400() {
        ResponseEntity<Map<String, Object>> resp = handler.onInvalidAmount(new InvalidAmountException(42));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("金額不正確", errorOf(resp));
    }

    @Test
    @DisplayName("400 VALIDATION_ERROR: IllegalArgumentException → 400, keeps the thrown zh-TW message")
    void illegalArgumentIs400() {
        ResponseEntity<Map<String, Object>> resp = handler.onBadRequest(new IllegalArgumentException("缺 data"));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("缺 data", errorOf(resp));
    }

    @Test
    @DisplayName("401 UNAUTHORIZED: UnauthorizedException → 401, documented message")
    void unauthorizedIs401() {
        ResponseEntity<Map<String, Object>> resp = handler.onUnauthorized(new UnauthorizedException("請先登入"));
        assertEquals(401, resp.getStatusCode().value());
        assertEquals("請先登入", errorOf(resp));
    }

    @Test
    @DisplayName("402 PAYMENT_REQUIRED: InsufficientBalanceException → 402 餘額不足")
    void insufficientBalanceIs402() {
        ResponseEntity<Map<String, Object>> resp = handler.onInsufficientBalance(new InsufficientBalanceException(0));
        assertEquals(402, resp.getStatusCode().value());
        assertEquals("餘額不足", errorOf(resp));
    }

    @Test
    @DisplayName("404 NOT_FOUND: unmapped route → 404 {\"error\":\"api not found\"} (not 500)")
    void noResourceIs404() {
        ResponseEntity<Map<String, Object>> resp =
                handler.onNotFound(new NoResourceFoundException(HttpMethod.GET, "/wallet/bogus"));
        assertEquals(404, resp.getStatusCode().value());
        assertEquals("api not found", errorOf(resp));
    }

    @Test
    @DisplayName("404 NOT_FOUND: NoHandlerFoundException → 404 api not found")
    void noHandlerIs404() {
        ResponseEntity<Map<String, Object>> resp = handler.onNotFound(
                new NoHandlerFoundException("GET", "/nope", org.springframework.http.HttpHeaders.EMPTY));
        assertEquals(404, resp.getStatusCode().value());
        assertEquals("api not found", errorOf(resp));
    }

    @Test
    @DisplayName("409 CONFLICT: DuplicateKeyException (concurrent buy race) → 409 已經擁有這個項目")
    void duplicateKeyIs409() {
        ResponseEntity<Map<String, Object>> resp = handler.onDuplicateKey(new DuplicateKeyException("unique"));
        assertEquals(409, resp.getStatusCode().value());
        assertEquals("已經擁有這個項目", errorOf(resp));
    }

    @Test
    @DisplayName("409 CONFLICT: IdempotencyConflictException → 409, carries its message")
    void idempotencyConflictIs409() {
        ResponseEntity<Map<String, Object>> resp =
                handler.onIdempotencyConflict(new IdempotencyConflictException("idempotency key 衝突"));
        assertEquals(409, resp.getStatusCode().value());
        assertEquals("idempotency key 衝突", errorOf(resp));
    }

    @Test
    @DisplayName("409 CONFLICT: duplicate-email registration → 409 這個 email 已經註冊過了 (member BC advice)")
    void duplicateEmailIs409() {
        ResponseEntity<Map<String, Object>> resp =
                new MemberExceptionAdvice().onDuplicateEmail(new EmailAlreadyExistsException());
        assertEquals(409, resp.getStatusCode().value());
        assertEquals("這個 email 已經註冊過了", errorOf(resp));
    }

    @Test
    @DisplayName("409 CONFLICT: already-owned purchase → 409 已經擁有這個項目 (entitlement BC advice)")
    void alreadyOwnedIs409() {
        ResponseEntity<Map<String, Object>> resp =
                new EntitlementExceptionAdvice().onAlreadyOwned(new AlreadyOwnedException());
        assertEquals(409, resp.getStatusCode().value());
        assertEquals("已經擁有這個項目", errorOf(resp));
    }

    @Test
    @DisplayName("500 INTERNAL_ERROR: unexpected exception → 500 {\"error\":\"internal error\"}, no stack leaked")
    void unexpectedIs500() {
        ResponseEntity<Map<String, Object>> resp =
                handler.onUnexpected(new RuntimeException("boom at com.internal.Secret SQL=SELECT *"));
        assertEquals(500, resp.getStatusCode().value());
        assertEquals("internal error", errorOf(resp));
    }
}
