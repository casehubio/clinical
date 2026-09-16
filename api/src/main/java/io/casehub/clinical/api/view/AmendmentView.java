package io.casehub.clinical.api.view;

public record AmendmentView(String id, String trialId, String proposedChange,
                              String status, String amendmentCaseStatus,
                              String proposedAt) {}
