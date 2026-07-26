package com.aubin.payment.infrastructure.adapter.out.cache;

import com.aubin.payment.application.port.out.IdempotencyStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class RedisIdempotencyAdapter implements IdempotencyStore {

    private static final String KEY_PREFIX = "idempotency:payment:";

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryMark(UUID eventId) {
        String key = KEY_PREFIX + eventId;

        Boolean wasAbsent = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);

        // Redis is down. Fail open and risk a duplicate rather than drop a legitimate payment
        if (wasAbsent == null) {
            return true;
        }
        return wasAbsent;
    }
}
