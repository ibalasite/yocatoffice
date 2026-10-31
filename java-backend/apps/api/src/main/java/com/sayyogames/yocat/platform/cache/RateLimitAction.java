package com.sayyogames.yocat.platform.cache;

/**
 * Rate-limited endpoints and their sliding-window budgets (EDD §11.3C 限流鍵設計 — verbatim table).
 *
 * <p>Each action maps to the EDD §11.3C key action segment, window length, and per-window request
 * limit. The {@code identifier} dimension (IP vs account) is documented per row; the caller
 * (platform {@code RateLimitFilter}, EDD §4.5.1 / §11.3C) supplies the concrete value.</p>
 *
 * <table>
 *   <caption>EDD §11.3C</caption>
 *   <tr><th>Endpoint</th><th>action</th><th>window_sec</th><th>limit</th><th>identifier</th></tr>
 *   <tr><td>POST /auth/login</td><td>login</td><td>60</td><td>10</td><td>IP</td></tr>
 *   <tr><td>POST /auth/register</td><td>register</td><td>60</td><td>5</td><td>IP</td></tr>
 *   <tr><td>POST /wallet/topup,/spend</td><td>wallet</td><td>60</td><td>60</td><td>account</td></tr>
 *   <tr><td>POST /entitlements/buy</td><td>buy</td><td>60</td><td>30</td><td>account</td></tr>
 *   <tr><td>GET /leaderboard</td><td>lb</td><td>60</td><td>120</td><td>IP</td></tr>
 * </table>
 */
public enum RateLimitAction {

    LOGIN("login", 60, 10),
    REGISTER("register", 60, 5),
    WALLET("wallet", 60, 60),
    BUY("buy", 60, 30),
    LEADERBOARD("lb", 60, 120);

    private final String action;
    private final int windowSeconds;
    private final int limit;

    RateLimitAction(String action, int windowSeconds, int limit) {
        this.action = action;
        this.windowSeconds = windowSeconds;
        this.limit = limit;
    }

    /** EDD §11.3C key action segment ({@code {env}:rate:{action}:{identifier}}). */
    public String action() {
        return action;
    }

    public int windowSeconds() {
        return windowSeconds;
    }

    public long windowMillis() {
        return windowSeconds * 1000L;
    }

    public int limit() {
        return limit;
    }
}
