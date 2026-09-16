package io.casehub.clinical.api.view;

import java.time.Instant;
import java.util.UUID;

public record GradeHistoryEntry(UUID id, String previousGrade, String newGrade,
                                  Instant changedAt, String changedBy, String reason) {}
