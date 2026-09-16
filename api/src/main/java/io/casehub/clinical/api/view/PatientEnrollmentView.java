package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.EligibilityScreeningCaseStatus;
import io.casehub.clinical.api.model.EligibilityScreeningResult;
import io.casehub.clinical.api.model.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record PatientEnrollmentView(UUID id, UUID siteId, String patientId,
                                      ConsentStatus consentStatus, EnrollmentStatus enrollmentStatus,
                                      Instant enrolledAt, Instant withdrawnAt,
                                      EligibilityScreeningResult screeningResult,
                                      Instant screeningCompletedAt,
                                      EligibilityScreeningCaseStatus eligibilityScreeningCaseStatus,
                                      String treatmentArm) {}
