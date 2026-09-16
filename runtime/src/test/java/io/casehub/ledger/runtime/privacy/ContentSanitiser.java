package io.casehub.ledger.runtime.privacy;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Compatibility stub for stale casehub-qhorus SNAPSHOT that still references the old
 * ContentSanitiser package. Remove when qhorus is rebuilt against ledger-core.
 */
@ApplicationScoped
@DefaultBean
public class ContentSanitiser {
    public String sanitise(String decisionContextJson) {
        return decisionContextJson;
    }
}
