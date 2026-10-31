package com.sayyogames.yocat.record.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sayyogames.yocat.platform.web.CurrentUser;
import com.sayyogames.yocat.record.application.RecordAppService;
import com.sayyogames.yocat.record.domain.BusinessRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * record BC presentation layer — append-only business history (API §3 record: {@code POST /records},
 * {@code GET /records}). Serialises the request {@code payload} object to JSONB text for storage and parses the
 * stored JSON back to a nested object on read; validation (type) and persistence are delegated to
 * {@link RecordAppService}. The list is returned under {@code records} newest-first, optionally filtered by type.
 */
@RestController
public class RecordController {

    private final RecordAppService recordService;
    private final ObjectMapper mapper;

    public RecordController(RecordAppService recordService, ObjectMapper mapper) {
        this.recordService = recordService;
        this.mapper = mapper;
    }

    @PostMapping(path = "/records", produces = "application/json")
    public Map<String, Object> create(CurrentUser user, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        String type = b.get("type") == null ? null : b.get("type").toString();
        String payloadJson = writeJson(b.get("payload"));
        recordService.add(user.accountId(), type, payloadJson);
        return Map.of("ok", true);
    }

    @GetMapping(path = "/records", produces = "application/json")
    public List<Map<String, Object>> list(
            CurrentUser user, @RequestParam(value = "type", required = false) String type) {
        return recordService.list(user.accountId(), type).stream().map(this::recordView).toList();
    }

    private Map<String, Object> recordView(BusinessRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", r.type());
        m.put("payload", readJson(r.payload()));
        m.put("createdAt", r.createdAt() == null ? null : r.createdAt().toString());
        return m;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "{}";
        }
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
