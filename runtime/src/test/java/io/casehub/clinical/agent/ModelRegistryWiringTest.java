package io.casehub.clinical.agent;

import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelRegistry;
import io.casehub.platform.api.model.ModelTier;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {"SPONSOR"})
class ModelRegistryWiringTest {

    @Inject ModelRegistry modelRegistry;

    @Test
    void modelRegistryIsInjectable() {
        assertThat(modelRegistry).isNotNull();
    }

    @Test
    void queryByTierReturnsResult() {
        var results = modelRegistry.query(
                ModelQuery.builder().tier(ModelTier.FLAGSHIP).build());
        assertThat(results).isNotNull();
    }

    @Test
    void allReturnsResult() {
        var all = modelRegistry.all();
        assertThat(all).isNotNull();
    }
}
