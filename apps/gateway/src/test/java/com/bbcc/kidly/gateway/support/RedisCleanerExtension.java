package com.bbcc.kidly.gateway.support;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 테스트의 @AfterEach(리스너 멈춤) 뒤에 돌아, 멈추는 리스너가 비운 뒤에 다시 쓰지 않는다.
 */
public class RedisCleanerExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        var redis = SpringExtension.getApplicationContext(context).getBean(StringRedisTemplate.class);
        flush(redis);
    }

    public static void flush(StringRedisTemplate redis) {
        redis.execute(connection -> {
            connection.serverCommands().flushDb();
            return null;
        }, true);
    }
}
