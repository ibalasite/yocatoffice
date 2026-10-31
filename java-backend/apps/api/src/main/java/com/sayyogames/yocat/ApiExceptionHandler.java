package com.sayyogames.yocat;

import com.sayyogames.yocat.platform.web.IdempotencyConflictException;
import com.sayyogames.yocat.platform.web.UnauthorizedException;
import com.sayyogames.yocat.shared.InvalidAmountException;
import com.sayyogames.yocat.wallet.domain.InsufficientBalanceException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central mapping of domain / application / presentation errors to the documented HTTP contract
 * (API.md §5.2 Error Code Registry). Every error body carries an {@code error} string field
 * (API §5.2) so clients get a single, stable error shape.
 *
 * <p>Status mapping (API §5.2 Error Code Registry): validation → 400 ({@code VALIDATION_ERROR});
 * unauthenticated → 401 ({@code UNAUTHORIZED}); insufficient balance → 402 ({@code PAYMENT_REQUIRED});
 * unmapped route → 404 ({@code NOT_FOUND}, {@code {"error":"api not found"}}); conflict
 * (duplicate email / already-owned / idempotency) → 409 ({@code CONFLICT}); any unexpected exception →
 * 500 ({@code INTERNAL_ERROR}, {@code {"error":"internal error"}}, no stack/SQL leaked per §5.1).
 * Rate limiting (429 {@code RATE_LIMITED}) is produced upstream by the BearerTokenFilter / RateLimiter,
 * and 413 {@code PAYLOAD_TOO_LARGE} / 503 {@code SERVICE_UNAVAILABLE} (graceful shutdown) are enforced at
 * the servlet-container / lifecycle layer — see API §5.2. The 402 insufficient-balance body additionally
 * carries the current wallet; wallet / entitlement controllers build that richer body themselves, and this
 * advice is the generic fallback.</p>
 *
 * <p>Lives in the application root package (not the {@code platform} module): the advice references the
 * exception types of several bounded contexts, so placing it inside a module would make that module depend on
 * all the others. The application root is outside the Spring Modulith module boundaries and is the correct
 * home for this cross-module presentation concern (EDD §3.4 HC-5 DAG).</p>
 *
 * <p>Ordered {@link Ordered#LOWEST_PRECEDENCE} so the per-BC advices (which own their module's specific
 * exceptions, e.g. duplicate email / already-owned) are consulted first; this generic advice — including its
 * {@code Exception.class} catch-all — only applies to exceptions no BC advice claims.</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> onUnauthorized(UnauthorizedException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> onDuplicateKey(DuplicateKeyException ex) {
        // UNIQUE(account_id, kind, item_id) lost race on concurrent buy → 409 (API §5.2).
        return error(HttpStatus.CONFLICT, "已經擁有這個項目");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> onIdempotencyConflict(IdempotencyConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> onInsufficientBalance(InsufficientBalanceException ex) {
        return error(HttpStatus.PAYMENT_REQUIRED, "餘額不足");
    }

    @ExceptionHandler(InvalidAmountException.class)
    public ResponseEntity<Map<String, Object>> onInvalidAmount(InvalidAmountException ex) {
        return error(HttpStatus.BAD_REQUEST, "金額不正確");
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> onBadRequest(Exception ex) {
        String message = ex.getMessage() == null ? "請求格式不正確" : ex.getMessage();
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> onNotFound(Exception ex) {
        // API §5.2: a request matched a prefix but no route → 404 {"error":"api not found"}.
        // Declared explicitly so the unmapped-route case does not fall through to the generic
        // Exception.class catch-all below (which would mis-report it as 500).
        return error(HttpStatus.NOT_FOUND, "api not found");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> onUnexpected(Exception ex) {
        // API §5.2 / §5.1: 500 never leaks stack/SQL — only a safe message string (traced via logs).
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class)
                .error("Unhandled API error → 500", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal error");
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
