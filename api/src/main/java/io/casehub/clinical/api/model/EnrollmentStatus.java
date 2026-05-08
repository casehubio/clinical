package io.casehub.clinical.api.model;

/** Subject participation state per FHIR ResearchSubject.subjectState. */
public enum EnrollmentStatus {
    CANDIDATE, SCREENING, ELIGIBLE, INELIGIBLE, ENROLLED, ON_STUDY, OFF_STUDY, WITHDRAWN
}
