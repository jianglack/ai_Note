package com.ainote.app.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsBlankTitleAndMissingStartTime() {
        ScheduleRequest request = new ScheduleRequest();
        request.setTitle(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("title", "startTime");
    }

    @Test
    void rejectsNegativeReminderMinutes() {
        ScheduleRequest request = new ScheduleRequest();
        request.setTitle("Review");
        request.setStartTime(LocalDateTime.now());
        request.setReminderMinutes(-1);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("reminderMinutes");
    }
}
