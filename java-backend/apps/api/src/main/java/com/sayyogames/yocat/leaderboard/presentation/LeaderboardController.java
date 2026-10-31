package com.sayyogames.yocat.leaderboard.presentation;

import com.sayyogames.yocat.leaderboard.application.LeaderboardAppService;
import com.sayyogames.yocat.leaderboard.domain.RunScore;
import com.sayyogames.yocat.platform.web.CurrentUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * leaderboard BC presentation layer — public Top-N query &amp; attributed submit (API §3 leaderboard:
 * {@code GET /leaderboard} public, {@code POST /leaderboard} authenticated). The list is a JSON array ordered by
 * score DESC with the count clamped to [1,100]; submit attributes the run to the authenticated member's display
 * name. No business logic here (EDD §4.5.2).
 */
@RestController
public class LeaderboardController {

    private static final int DEFAULT_LIMIT = 20;

    private final LeaderboardAppService leaderboardService;

    public LeaderboardController(LeaderboardAppService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping(path = "/leaderboard", produces = "application/json")
    public List<Map<String, Object>> list(
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "limit", required = false) Integer limit) {
        int requested = limit == null ? DEFAULT_LIMIT : limit;
        return leaderboardService.topN(mode, requested).stream().map(LeaderboardController::entryView).toList();
    }

    @PostMapping(path = "/leaderboard", produces = "application/json")
    public Map<String, Object> submit(CurrentUser user, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        leaderboardService.submit(
                user.accountId(), user.displayName(),
                str(b, "runId"), str(b, "mode"), longVal(b, "score"), str(b, "grade"));
        return Map.of("ok", true);
    }

    private static Map<String, Object> entryView(RunScore r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", r.runId());
        m.put("name", r.displayName());
        m.put("displayName", r.displayName());
        m.put("mode", r.mode());
        m.put("score", r.score());
        if (r.grade() != null) {
            m.put("grade", r.grade());
        }
        return m;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static long longVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException nfe) {
            return 0L;
        }
    }
}
