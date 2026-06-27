package io.casehub.clinical.demo;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DemoCurrentPrincipalTest {

    private final DemoCurrentPrincipal principal = new DemoCurrentPrincipal();

    @Test
    void tenancyId_returns_demo_tenant() {
        assertThat(principal.tenancyId()).isEqualTo("demo-tenant");
    }

    @Test
    void actorId_returns_demo_user() {
        assertThat(principal.actorId()).isEqualTo("demo-user");
    }
}
