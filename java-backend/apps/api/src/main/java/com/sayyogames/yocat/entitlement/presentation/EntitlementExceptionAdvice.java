package com.sayyogames.yocat.entitlement.presentation;

import com.sayyogames.yocat.entitlement.application.AlreadyOwnedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * entitlement BC presentation error mapping — keeps the entitlement-owned application exception
 * ({@code AlreadyOwnedException}) inside the entitlement module instead of a central cross-module advice, so the
 * application root does not depend on entitlement internals (EDD §3.4 HC-5 DAG). Maps an already-owned purchase to
 * the documented 409 contract (API §5.2). Composes with the generic application {@code ApiExceptionHandler}.
 */
@RestControllerAdvice
@Order(0)
public class EntitlementExceptionAdvice {

    @ExceptionHandler(AlreadyOwnedException.class)
    public ResponseEntity<Map<String, Object>> onAlreadyOwned(AlreadyOwnedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
