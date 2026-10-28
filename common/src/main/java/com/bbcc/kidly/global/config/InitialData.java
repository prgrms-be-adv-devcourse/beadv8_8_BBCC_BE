package com.bbcc.kidly.global.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Profile;

/**
 * local · dev 프로필에서만 빈으로 등록한다. 기획서 7.1 공통 초기 데이터를 넣는 러너에 붙인다.
 * test 프로필은 빈 DB에서 시작해야 하므로 넣지 않는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Profile({"local", "dev"})
public @interface InitialData {
}
