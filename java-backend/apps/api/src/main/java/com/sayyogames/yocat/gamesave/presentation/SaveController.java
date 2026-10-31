package com.sayyogames.yocat.gamesave.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sayyogames.yocat.gamesave.application.SaveAppService;
import com.sayyogames.yocat.gamesave.domain.OfficeSave;
import com.sayyogames.yocat.platform.web.CurrentUser;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * game-save BC presentation layer — cross-device save sync (API §3 game-save: {@code PUT /save},
 * {@code GET /save}). PUT requires a {@code data} object (absent → 400 {@code "缺 data"}) and upserts by slot;
 * GET of an empty slot returns {@code {"save": null}} with 200 (never 404). The stored JSONB is parsed back to a
 * nested object on read.
 */
@RestController
public class SaveController {

    private static final String DEFAULT_SLOT = "main";

    private final SaveAppService saveService;
    private final ObjectMapper mapper;

    public SaveController(SaveAppService saveService, ObjectMapper mapper) {
        this.saveService = saveService;
        this.mapper = mapper;
    }

    @PutMapping(path = "/save", produces = "application/json")
    public Map<String, Object> put(CurrentUser user, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        if (!b.containsKey("data") || b.get("data") == null) {
            throw new IllegalArgumentException("缺 data");
        }
        String slot = b.get("slot") == null ? DEFAULT_SLOT : b.get("slot").toString();
        saveService.put(user.accountId(), slot, writeJson(b.get("data")));
        return Map.of("ok", true);
    }

    @GetMapping(path = "/save", produces = "application/json")
    public Map<String, Object> get(
            CurrentUser user, @RequestParam(value = "slot", required = false) String slot) {
        String resolvedSlot = (slot == null || slot.isBlank()) ? DEFAULT_SLOT : slot;
        Optional<OfficeSave> save = saveService.get(user.accountId(), resolvedSlot);

        Map<String, Object> resp = new LinkedHashMap<>();
        if (save.isEmpty()) {
            resp.put("save", null); // empty slot → {"save": null} with 200 (API §3, not 404)
            return resp;
        }
        Object data = readJson(save.get().data());
        String updatedAt = Instant.now().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", data);
        envelope.put("updatedAt", updatedAt);
        resp.put("save", envelope);
        resp.put("data", data);           // convenience alias for clients reading the save body directly
        resp.put("updatedAt", updatedAt);
        return resp;
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }
}
