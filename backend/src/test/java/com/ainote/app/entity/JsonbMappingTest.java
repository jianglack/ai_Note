package com.ainote.app.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonbMappingTest {

    @Test
    void taskStepJsonbStringsUseJsonJdbcType() throws Exception {
        assertJsonType(TaskStep.class, "inputParams");
        assertJsonType(TaskStep.class, "outputResult");
        assertJsonType(TaskStep.class, "compensation");
    }

    @Test
    void sideEffectJournalJsonbStringsUseJsonJdbcType() throws Exception {
        assertJsonType(SideEffectJournal.class, "outputSnapshot");
        assertJsonType(SideEffectJournal.class, "journalJson");
        assertJsonType(SideEffectJournal.class, "rollbackResult");
    }

    private void assertJsonType(Class<?> entityClass, String fieldName) throws Exception {
        JdbcTypeCode jdbcTypeCode = entityClass.getDeclaredField(fieldName).getAnnotation(JdbcTypeCode.class);

        assertThat(jdbcTypeCode)
                .as(entityClass.getSimpleName() + "." + fieldName)
                .isNotNull();
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.JSON);
    }
}
