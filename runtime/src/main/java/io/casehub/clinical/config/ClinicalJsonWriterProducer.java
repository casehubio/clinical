package io.casehub.clinical.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.pages.push.JsonWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ClinicalJsonWriterProducer {

    @Produces
    @ApplicationScoped
    JsonWriter jsonWriter() {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper::writeValueAsString;
    }
}
