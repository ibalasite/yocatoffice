package com.sayyogames.yocat.platform.web;

/**
 * Presentation error — the request carries no valid session (missing / expired / invalid Bearer token)
 * or the credentials are wrong. Maps to HTTP 401 (API §5.2 self-scoped trust model: no 403/422; any
 * authorization failure collapses to 401). The message is the documented, caller-facing string.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
