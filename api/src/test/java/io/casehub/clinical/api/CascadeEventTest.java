package io.casehub.clinical.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CascadeEventTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void roundTrip() throws Exception {
        CascadeEvent event = new CascadeEvent(
            CascadeStepType.AE_REPORTED, CascadeStepStatus.COMPLETED,
            Instant.parse("2026-09-14T10:00:00Z"), "system",
            "Grade 4 AE reported", Map.of("grade", "GRADE_4"));
        String json = mapper.writeValueAsString(event);
        CascadeEvent parsed = mapper.readValue(json, CascadeEvent.class);
        assertThat(parsed.step()).isEqualTo(CascadeStepType.AE_REPORTED);
        assertThat(parsed.status()).isEqualTo(CascadeStepStatus.COMPLETED);
        assertThat(parsed.actor()).isEqualTo("system");
        assertThat(parsed.detail()).isEqualTo("Grade 4 AE reported");
    }

    @Test
    void nullDataAllowed() throws Exception {
        CascadeEvent event = new CascadeEvent(
            CascadeStepType.LEDGER_SEALED, CascadeStepStatus.PENDING,
            null, null, null, null);
        String json = mapper.writeValueAsString(event);
        CascadeEvent parsed = mapper.readValue(json, CascadeEvent.class);
        assertThat(parsed.step()).isEqualTo(CascadeStepType.LEDGER_SEALED);
        assertThat(parsed.status()).isEqualTo(CascadeStepStatus.PENDING);
        assertThat(parsed.timestamp()).isNull();
    }
}
