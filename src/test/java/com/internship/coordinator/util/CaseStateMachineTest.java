package com.internship.coordinator.util;

import com.internship.coordinator.model.CaseStatus;
import com.internship.coordinator.model.Recommendation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseStateMachineTest {

    private CaseStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new CaseStateMachine();
    }

    @Test
    void approveFromReadyForReviewMovesToApproved() {
        assertEquals(
                CaseStatus.APPROVED,
                stateMachine.resolveCoordinatorDecision(CaseStatus.READY_FOR_REVIEW, Recommendation.APPROVE));
    }

    @Test
    void rejectFromPendingSupervisorMovesToRejected() {
        assertEquals(
                CaseStatus.REJECTED,
                stateMachine.resolveCoordinatorDecision(CaseStatus.PENDING_SUPERVISOR, Recommendation.REJECT));
    }

    @Test
    void clarifyFromClarificationRequestedMovesToNeedsClarification() {
        assertEquals(
                CaseStatus.NEEDS_CLARIFICATION,
                stateMachine.resolveCoordinatorDecision(CaseStatus.CLARIFICATION_REQUESTED, Recommendation.CLARIFY));
    }

    @ParameterizedTest
    @EnumSource(
            value = CaseStatus.class,
            names = {"READY_FOR_REVIEW", "PENDING_SUPERVISOR", "CLARIFICATION_REQUESTED"})
    void allowsCoordinatorDecisionFromReviewStates(CaseStatus status) {
        assertTrue(stateMachine.allowsCoordinatorDecision(status));
    }

    @ParameterizedTest
    @EnumSource(
            value = CaseStatus.class,
            names = {"NEW", "EXTRACTING", "NEEDS_CLARIFICATION", "APPROVED", "REJECTED"})
    void rejectsCoordinatorDecisionFromOtherStates(CaseStatus status) {
        assertFalse(stateMachine.allowsCoordinatorDecision(status));
    }

    @Test
    void rejectsDecisionFromTerminalApprovedStatus() {
        assertThrows(
                IllegalStateException.class,
                () -> stateMachine.resolveCoordinatorDecision(CaseStatus.APPROVED, Recommendation.REJECT));
    }

    @Test
    void rejectsDecisionFromNewStatus() {
        assertThrows(
                IllegalStateException.class,
                () -> stateMachine.resolveCoordinatorDecision(CaseStatus.NEW, Recommendation.APPROVE));
    }

    @ParameterizedTest
    @CsvSource({
        "READY_FOR_REVIEW, APPROVE, APPROVED",
        "READY_FOR_REVIEW, REJECT, REJECTED",
        "READY_FOR_REVIEW, CLARIFY, NEEDS_CLARIFICATION",
        "PENDING_SUPERVISOR, APPROVE, APPROVED",
        "PENDING_SUPERVISOR, REJECT, REJECTED",
        "PENDING_SUPERVISOR, CLARIFY, NEEDS_CLARIFICATION",
        "CLARIFICATION_REQUESTED, APPROVE, APPROVED",
        "CLARIFICATION_REQUESTED, REJECT, REJECTED",
        "CLARIFICATION_REQUESTED, CLARIFY, NEEDS_CLARIFICATION"
    })
    void resolveCoordinatorDecisionForAllowedStatuses(
            CaseStatus currentStatus, Recommendation decision, CaseStatus expectedStatus) {
        assertEquals(expectedStatus, stateMachine.resolveCoordinatorDecision(currentStatus, decision));
    }

    @ParameterizedTest
    @EnumSource(value = CaseStatus.class, names = {"APPROVED", "REJECTED"})
    void rejectsDecisionFromTerminalStatuses(CaseStatus terminalStatus) {
        assertThrows(
                IllegalStateException.class,
                () -> stateMachine.resolveCoordinatorDecision(terminalStatus, Recommendation.APPROVE));
    }
}
