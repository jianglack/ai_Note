package com.ainote.app.entity;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class UserMemoryTest {

    @Test
    void userIdColumnCanStorePlanStepMemoryKey() throws Exception {
        Field userId = UserMemory.class.getDeclaredField("userId");
        Column column = userId.getAnnotation(Column.class);

        String planStepMemoryKey = "plan:df9aa9b1-1234-4567-8888-abcdef123456:step:1";

        assertThat(column.length())
                .as("user_memories.user_id must store plan step memory keys, not only 36-char user ids")
                .isGreaterThanOrEqualTo(planStepMemoryKey.length());
    }
}
