package com.ainote.app;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 应用启动集成测试
 * 需要数据库连接，暂时禁用
 * 单元测试已覆盖核心功能
 */
@Disabled("需要数据库连接，使用单元测试替代")
@DisplayName("应用启动测试")
class AiNoteApplicationTest {

    @Test
    @DisplayName("应用上下文应能正常加载")
    void contextLoads() {
        // 如果应用上下文能正常加载，测试就会通过
    }
}
