package com.ainote.app.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthResponseSecurityTest {

    @Test
    void jwtIsAvailableToCookieWriterButNeverSerializedToJson() throws Exception {
        AuthResponse response = new AuthResponse("jwt-secret", "user-1", "alice", "alice@example.invalid");

        assertThat(response.getToken()).isEqualTo("jwt-secret");
        assertThat(new ObjectMapper().writeValueAsString(response))
                .doesNotContain("jwt-secret")
                .doesNotContain("token")
                .contains("\"userId\":\"user-1\"");
    }
}
