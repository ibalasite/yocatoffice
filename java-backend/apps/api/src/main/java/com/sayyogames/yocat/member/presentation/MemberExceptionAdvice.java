package com.sayyogames.yocat.member.presentation;

import com.sayyogames.yocat.member.application.EmailAlreadyExistsException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * member BC presentation error mapping — keeps the member-owned application exception
 * ({@code EmailAlreadyExistsException}) inside the member module instead of a central cross-module advice, so the
 * application root does not depend on member internals (EDD §3.4 HC-5 DAG). Maps duplicate-email registration to
 * the documented 409 contract (API §5.2). Composes with the generic application {@code ApiExceptionHandler}.
 */
@RestControllerAdvice
@Order(0)
public class MemberExceptionAdvice {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> onDuplicateEmail(EmailAlreadyExistsException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "這個 email 已經註冊過了");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
