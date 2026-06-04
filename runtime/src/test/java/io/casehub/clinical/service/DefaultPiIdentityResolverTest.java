package io.casehub.clinical.service;

import io.casehub.clinical.api.spi.PiIdentityResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPiIdentityResolverTest {

    private final PiIdentityResolver resolver = new DefaultPiIdentityResolver();

    @Test
    void resolves_unknown_actor_id_to_itself() {
        assertThat(resolver.resolveFormalName("claude:pi@v1")).isEqualTo("claude:pi@v1");
    }

    @Test
    void resolves_human_actor_id_to_itself() {
        assertThat(resolver.resolveFormalName("dr-smith@institution.org")).isEqualTo("dr-smith@institution.org");
    }
}
