package com.internship.coordinator.util;

import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.Recommendation;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CaseStateMachine {

    private static final Set<CaseStatus> DECISION_ALLOWED_STATUSES = EnumSet.of(
            CaseStatus.READY_FOR_REVIEW, CaseStatus.PENDING_SUPERVISOR, CaseStatus.CLARIFICATION_REQUESTED);

    private static final Set<CaseStatus> TERMINAL_STATUSES = EnumSet.of(CaseStatus.APPROVED, CaseStatus.REJECTED);

    private static final Map<Recommendation, CaseStatus> DECISION_TARGETS = Map.of(
            Recommendation.APPROVE, CaseStatus.APPROVED,
            Recommendation.REJECT, CaseStatus.REJECTED,
            Recommendation.CLARIFY, CaseStatus.NEEDS_CLARIFICATION);

    public boolean allowsCoordinatorDecision(CaseStatus currentStatus) {
        return DECISION_ALLOWED_STATUSES.contains(currentStatus);
    }

    public CaseStatus resolveCoordinatorDecision(CaseStatus currentStatus, Recommendation decision) {
        if (TERMINAL_STATUSES.contains(currentStatus)) {
            throw new IllegalStateException("Case is already in a terminal status: " + currentStatus);
        }
        if (!DECISION_ALLOWED_STATUSES.contains(currentStatus)) {
            throw new IllegalStateException("Coordinator decision is not allowed from status: " + currentStatus);
        }
        CaseStatus targetStatus = DECISION_TARGETS.get(decision);
        if (targetStatus == null) {
            throw new IllegalStateException("Unsupported coordinator decision: " + decision);
        }
        return targetStatus;
    }
}
