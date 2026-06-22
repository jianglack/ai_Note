package com.ainote.app.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KnowledgeGraphService transactions")
class KnowledgeGraphServiceTransactionTest {

    @Test
    void syncMethodsAreNotReadOnlyBecauseTheyWriteNeo4j() throws Exception {
        for (String methodName : Stream.of("syncNote", "syncFolder", "syncSchedule").toList()) {
            Method method = KnowledgeGraphService.class.getMethod(methodName, String.class);
            Transactional transactional = method.getAnnotation(Transactional.class);

            assertThat(transactional)
                    .as(methodName + " should stay transactional")
                    .isNotNull();
            assertThat(transactional.readOnly())
                    .as(methodName + " writes graph state")
                    .isFalse();
        }
    }
}
