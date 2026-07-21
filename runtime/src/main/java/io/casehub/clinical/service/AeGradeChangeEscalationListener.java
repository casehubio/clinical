package io.casehub.clinical.service;

import io.casehub.clinical.api.AeGradeChangedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class AeGradeChangeEscalationListener {

    @Inject AeEscalationCaseService escalationService;

    public void onGradeChanged(@ObservesAsync AeGradeChangedEvent event) {
        if (!event.isUpgrade()) return;
        escalationService.startEscalationForRegrade(
            event.aeId(), event.enrollmentId(), event.siteId(), event.newGrade(), event.tenantId());
    }
}
