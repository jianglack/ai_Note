package com.ainote.app.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 精确 Token 计算服务
 * 使用 jtokkit（CL100K_BASE 编码器）替代粗糙的字符数估算
 */
@Service
public class JiTokenService {

    private final Encoding encoding;
    private final Cache<Integer, Integer> tokenCache;

    public JiTokenService() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
        this.tokenCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }

    public int countTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int key = text.hashCode();
        return tokenCache.get(key, k -> encoding.countTokens(text));
    }
}
