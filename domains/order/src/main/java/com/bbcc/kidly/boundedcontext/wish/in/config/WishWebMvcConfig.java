package com.bbcc.kidly.boundedcontext.wish.in.config;

import com.bbcc.kidly.boundedcontext.wish.app.port.WishActors;
import com.bbcc.kidly.boundedcontext.wish.in.web.WishCallerArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 조르기 호출 주체 해석기(@WishCaller, #906)를 등록한다. 공용 WebMvcConfig(@LoginMember 등)는 바꾸지 않고 더하기만 한다.
 * 스위치 kidly.order.wish.enabled가 false면 조르기 API와 함께 없다.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishWebMvcConfig implements WebMvcConfigurer {

    private final WishActors wishActors;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new WishCallerArgumentResolver(wishActors));
    }
}
