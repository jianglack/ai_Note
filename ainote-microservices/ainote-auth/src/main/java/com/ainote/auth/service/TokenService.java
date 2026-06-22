package com.ainote.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TokenService {

    private static final String TOKEN_PREFIX = "token:";
    private static final long TOKEN_EXPIRATION_HOURS = 24;

    private final StringRedisTemplate redisTemplate;

    public TokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeToken(String jti, String userId) {
        redisTemplate.opsForValue().set(
                TOKEN_PREFIX + jti, userId, TOKEN_EXPIRATION_HOURS, TimeUnit.HOURS);
    }

    public void deleteToken(String jti) {
        redisTemplate.delete(TOKEN_PREFIX + jti);
    }

    public boolean isTokenValid(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_PREFIX + jti));
    }
}
