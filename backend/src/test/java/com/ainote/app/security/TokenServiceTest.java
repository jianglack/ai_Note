package com.ainote.app.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(redisTemplate);
    }

    @Test
    void validateToken_rejectsJtiStoredForDifferentUser() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("token:jti-1")).thenReturn("user-a");

        assertThat(tokenService.validateToken("jti-1", "user-b")).isFalse();
    }

    @Test
    void validateToken_acceptsJtiStoredForSameUser() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("token:jti-1")).thenReturn("user-a");

        assertThat(tokenService.validateToken("jti-1", "user-a")).isTrue();
    }

    @Test
    void storeToken_indexesJtiByUserForFutureRevocation() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        tokenService.storeToken("jti-1", "user-a");

        verify(setOperations).add("user_tokens:user-a", "jti-1");
    }

    @Test
    void deleteAllTokensForUser_removesEveryTokenAndTheUserIndex() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("user_tokens:user-a")).thenReturn(Set.of("jti-1", "jti-2"));

        tokenService.deleteAllTokensForUser("user-a");

        verify(redisTemplate).delete("token:jti-1");
        verify(redisTemplate).delete("token:jti-2");
        verify(redisTemplate).delete("user_tokens:user-a");
    }
}
