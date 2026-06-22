package com.ainote.app.agent.guardrail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutputGuardrail 输出脱敏护栏测试")
class OutputGuardrailTest {

    private OutputGuardrail guardrail;

    @BeforeEach
    void setUp() {
        guardrail = new OutputGuardrail();
    }

    @Test
    @DisplayName("null 输入应原样返回")
    void shouldReturnNullAsIs() {
        assertThat(guardrail.sanitize(null, "user1")).isNull();
    }

    @Test
    @DisplayName("空字符串应原样返回")
    void shouldReturnEmptyAsIs() {
        assertThat(guardrail.sanitize("", "user1")).isEmpty();
    }

    @Test
    @DisplayName("正常文本不应被修改")
    void shouldNotModifyNormalText() {
        String normal = "已创建笔记「会议记录」（ID: abc-123）。";
        assertThat(guardrail.sanitize(normal, "user1")).isEqualTo(normal);
    }

    // ---- API Key 脱敏 ----

    @Test
    @DisplayName("sk 格式 API Key 应被脱敏")
    void shouldRedactSkApiKey() {
        String text = "配置文件中发现 api_key=sk-abcdefghijklmnopqrstuvwxyz1234567890 请检查";
        String result = guardrail.sanitize(text, "user1");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("sk-abcdefghijklmnopqrstuvwxyz1234567890");
    }

    @Test
    @DisplayName("独立的 sk- 格式 Key 应被脱敏")
    void shouldRedactSkKey() {
        String text = "密钥是 sk-aBcDeFgHiJkLmNoPqRsT12345678";
        String result = guardrail.sanitize(text, "user1");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("sk-aBcDeFgHiJkLmNoPqRsT12345678");
    }

    // ---- Bearer Token 脱敏 ----

    @Test
    @DisplayName("Bearer Token 应被脱敏")
    void shouldRedactBearerToken() {
        String text = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.abc.def";
        String result = guardrail.sanitize(text, "user1");
        assertThat(result).contains("[REDACTED]");
    }

    // ---- JWT 脱敏 ----

    @Test
    @DisplayName("JWT Token 应被脱敏")
    void shouldRedactJwtToken() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String text = "用户的 token 是 " + jwt + "，请妥善保管";
        String result = guardrail.sanitize(text, "user1");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain(jwt);
    }

    // ---- 数据库连接串脱敏 ----

    @Test
    @DisplayName("JDBC 连接串应被脱敏")
    void shouldRedactJdbcUrl() {
        String text = "数据库地址: jdbc://localhost:5432/ainote?user=admin&password=secret";
        String result = guardrail.sanitize(text, "user1");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("jdbc://localhost");
    }

    @Test
    @DisplayName("MongoDB 连接串应被脱敏")
    void shouldRedactMongoDbUrl() {
        String text = "连接 mongodb://admin:password@mongo.example.com:27017/db";
        String result = guardrail.sanitize(text, "user1");
        assertThat(result).contains("[REDACTED]");
    }

    @Test
    @DisplayName("Redis 连接串应被脱敏")
    void shouldRedactRedisUrl() {
        String text = "缓存地址 redis://default:mypassword@redis.example.com:6379";
        String result = guardrail.sanitize(text, "user1");
        assertThat(result).contains("[REDACTED]");
    }

    // ---- password/secret 赋值 ----

    @Test
    @DisplayName("password 赋值应被脱敏")
    void shouldRedactPasswordAssignment() {
        String text = "配置里有 password=MyS3cretP@ssw0rd 需要更换";
        String result = guardrail.sanitize(text, "user1");
        assertThat(result).contains("[REDACTED]");
        assertThat(result).doesNotContain("MyS3cretP@ssw0rd");
    }

    // ---- 混合场景 ----

    @Test
    @DisplayName("多种敏感信息混合应全部脱敏")
    void shouldRedactMultipleSensitivePatterns() {
        String text = "API Key: sk-abcdefghijklmnopqrstuvwxyz1234\n"
                + "数据库: jdbc://host:5432/db?user=admin\n"
                + "正常文本不受影响";
        String result = guardrail.sanitize(text, "user1");
        assertThat(result).doesNotContain("sk-abcdefghijklmnopqrstuvwxyz1234");
        assertThat(result).doesNotContain("jdbc://host:5432");
        assertThat(result).contains("正常文本不受影响");
    }
}
