package com.ainote.app.agent.budget;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenBudget Token 预算管理器测试")
class TokenBudgetTest {

    private TokenBudget budget;

    @BeforeEach
    void setUp() {
        budget = new TokenBudget();
        ReflectionTestUtils.setField(budget, "maxTokensPerRequest", 50000);
        ReflectionTestUtils.setField(budget, "warnThreshold", 40000);
        budget.reset();
    }

    @AfterEach
    void tearDown() {
        budget.reset();
    }

    @Test
    @DisplayName("初始 token 数应为 0")
    void shouldStartAtZero() {
        assertThat(budget.getCurrentTokens()).isEqualTo(0);
    }

    @Test
    @DisplayName("addTokens 应正确累加")
    void shouldAccumulateTokens() {
        budget.addTokens(1000);
        budget.addTokens(2000);
        assertThat(budget.getCurrentTokens()).isEqualTo(3000);
    }

    @Test
    @DisplayName("未达告警阈值时 isApproachingLimit 应为 false")
    void shouldNotApproachLimitBelow() {
        budget.addTokens(30000);
        assertThat(budget.isApproachingLimit()).isFalse();
    }

    @Test
    @DisplayName("达到告警阈值时 isApproachingLimit 应为 true")
    void shouldApproachLimitAtThreshold() {
        budget.addTokens(40000);
        assertThat(budget.isApproachingLimit()).isTrue();
    }

    @Test
    @DisplayName("超过最大值时 isExceeded 应为 true")
    void shouldBeExceededAboveMax() {
        budget.addTokens(50001);
        assertThat(budget.isExceeded()).isTrue();
    }

    @Test
    @DisplayName("剩余不足 5000 时应返回告警提示")
    void shouldReturnWarningHint() {
        budget.addTokens(46000); // 剩余 4000 < 5000
        String hint = budget.getBudgetHint();
        assertThat(hint).contains("预算即将用完");
        assertThat(hint).contains("4000");
    }

    @Test
    @DisplayName("超出预算时应返回超出提示")
    void shouldReturnExceededHint() {
        budget.addTokens(51000);
        String hint = budget.getBudgetHint();
        assertThat(hint).contains("预算已超出");
    }

    @Test
    @DisplayName("预算充足时不应返回提示")
    void shouldReturnEmptyHintWhenSufficient() {
        budget.addTokens(10000);
        String hint = budget.getBudgetHint();
        assertThat(hint).isEmpty();
    }

    @Test
    @DisplayName("reset 后应清零")
    void shouldResetToZero() {
        budget.addTokens(30000);
        budget.reset();
        assertThat(budget.getCurrentTokens()).isEqualTo(0);
        assertThat(budget.isApproachingLimit()).isFalse();
    }
}
