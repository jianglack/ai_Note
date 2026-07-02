package com.ainote.app.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainote.app.entity.TaskPlan;
import org.junit.jupiter.api.Test;

class PlanStateValidatorTest {

    private final PlanStateValidator validator = new PlanStateValidator();

    @Test
    void allowsValidTransitions() {
        assertThat(validator.canTransition(TaskPlan.STATUS_PLANNING, TaskPlan.STATUS_AWAITING_APPROVAL)).isTrue();
        assertThat(validator.canTransition(TaskPlan.STATUS_AWAITING_APPROVAL, TaskPlan.STATUS_EXECUTING)).isTrue();
        assertThat(validator.canTransition(TaskPlan.STATUS_EXECUTING, TaskPlan.STATUS_COMPLETED)).isTrue();
        assertThat(validator.canTransition(TaskPlan.STATUS_PAUSED, TaskPlan.STATUS_EXECUTING)).isTrue();
    }

    @Test
    void rejectsInvalidTransitions() {
        assertThat(validator.canTransition(TaskPlan.STATUS_COMPLETED, TaskPlan.STATUS_EXECUTING)).isFalse();
        assertThat(validator.canTransition("UNKNOWN", TaskPlan.STATUS_EXECUTING)).isFalse();

        assertThatThrownBy(() -> validator.validateTransition(TaskPlan.STATUS_COMPLETED, TaskPlan.STATUS_EXECUTING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid plan status transition");
    }

    @Test
    void identifiesTerminalStates() {
        assertThat(validator.isTerminal(TaskPlan.STATUS_COMPLETED)).isTrue();
        assertThat(validator.isTerminal(TaskPlan.STATUS_CANCELLED)).isTrue();
        assertThat(validator.isTerminal(TaskPlan.STATUS_CANCELLED_PARTIAL)).isTrue();
        assertThat(validator.isTerminal(TaskPlan.STATUS_EXECUTING)).isFalse();
        assertThat(validator.isTerminal("UNKNOWN")).isFalse();
    }
}
