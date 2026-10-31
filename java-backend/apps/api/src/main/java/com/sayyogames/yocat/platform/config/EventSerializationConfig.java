package com.sayyogames.yocat.platform.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sayyogames.yocat.shared.AccountId;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson support for serialising domain events that carry value objects into the Spring Modulith transactional
 * Outbox (event-publication registry, EDD §4.6). The registry serialises each published event (JSON) in the
 * producing transaction; the shared-kernel {@link AccountId} value object is a write-only wrapper with no
 * Jackson-visible shape, so without this module its serialisation fails and rolls the producing transaction
 * back.
 *
 * <p>Infrastructure-only: the value objects stay framework-free (no Jackson annotations in the domain). The
 * module is registered as a bean and picked up by the application {@code ObjectMapper} the registry uses.
 * {@code AccountId} marshals as its {@code long} identity, {@code Email} as its canonical string; enums,
 * {@code long}s and {@code java.time.Instant} (JSR-310) are handled by Boot's default modules.</p>
 */
@Configuration
public class EventSerializationConfig {

    @Bean
    public Module domainValueObjectJacksonModule() {
        SimpleModule module = new SimpleModule("yocat-domain-value-objects");

        module.addSerializer(AccountId.class, new JsonSerializer<>() {
            @Override
            public void serialize(AccountId value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeNumber(value.value());
            }
        });
        module.addDeserializer(AccountId.class, new JsonDeserializer<>() {
            @Override
            public AccountId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return AccountId.of(p.getLongValue());
            }
        });

        return module;
    }
}
