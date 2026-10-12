package com.bbcc.kidly.global.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Profile;

/**
 * local · test 프로필에서만 빈으로 등록한다. 운영에 열리면 안 되는 개발용 기능(예: 테스트 토큰 발급)에 붙인다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Profile({"local", "test"})
public @interface DevOnly {
}
