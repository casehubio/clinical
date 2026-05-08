package io.casehub.clinical.api.model;

/** Whether the adverse event actually occurred or was a near-miss. Per FHIR AdverseEvent.actuality. */
public enum EventActuality {
    ACTUAL, POTENTIAL
}
