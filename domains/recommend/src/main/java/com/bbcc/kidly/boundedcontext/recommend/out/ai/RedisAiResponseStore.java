package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.ai.AiResponseStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseType;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis로 구현한 {@link AiResponseStore}다. 키는 {@code recommend:ai:{종류}:{입력 해시}}이고 보관 기간은 Redis의 만료 시간이 맡는다.
 * Redis 오류는 잡지 않고 던진다. 부르는 쪽이 저장된 답이 없는 것처럼 처리한다.
 */
@Component
public class RedisAiResponseStore implements AiResponseStore {

    public static final String KEY_PREFIX = "recommend:ai:";

    private final StringRedisTemplate redisTemplate;

    public RedisAiResponseStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> find(AiResponseType type, AiResponseKey key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(redisKey(type, key)));
    }

    @Override
    public void save(AiResponseType type, AiResponseKey key, String responseJson, Duration timeToLive) {
        redisTemplate.opsForValue().set(redisKey(type, key), responseJson, timeToLive);
    }

    private String redisKey(AiResponseType type, AiResponseKey key) {
        return KEY_PREFIX + type.name() + ":" + key.value();
    }
}
