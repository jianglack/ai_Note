package com.ainote.app.security;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {

    private static final String TOKEN_PREFIX = "token:";
    private static final String USER_TOKENS_PREFIX = "user_tokens:";
    private static final long TOKEN_EXPIRATION_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;

    public TokenService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeToken(String jti, String userId) {
        redisTemplate.opsForValue().set(TOKEN_PREFIX + jti, userId, TOKEN_EXPIRATION_HOURS, TimeUnit.HOURS);
        redisTemplate.opsForSet().add(USER_TOKENS_PREFIX + userId, jti);
        redisTemplate.expire(USER_TOKENS_PREFIX + userId, TOKEN_EXPIRATION_HOURS, TimeUnit.HOURS);
    }

    public boolean validateToken(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_PREFIX + jti));
    }

    public boolean validateToken(String jti, String userId) {
        Object storedUserId = redisTemplate.opsForValue().get(TOKEN_PREFIX + jti);
        return userId != null && userId.equals(storedUserId);
    }

    public void deleteToken(String jti) {
        Object userId = redisTemplate.opsForValue().get(TOKEN_PREFIX + jti);
        redisTemplate.delete(TOKEN_PREFIX + jti);
        if (userId instanceof String ownerId) {
            redisTemplate.opsForSet().remove(USER_TOKENS_PREFIX + ownerId, jti);
        }
    }

    public void deleteAllTokensForUser(String userId) {
        String indexKey = USER_TOKENS_PREFIX + userId;
        Set<Object> jtis = redisTemplate.opsForSet().members(indexKey);
        if (jtis != null) {
            for (Object jti : jtis) {
                redisTemplate.delete(TOKEN_PREFIX + jti);
            }
        }
        redisTemplate.delete(indexKey);
    }
}
