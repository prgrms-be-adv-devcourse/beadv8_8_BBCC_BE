package com.bbcc.kidly.boundedcontext.cart.in.config;

import com.bbcc.kidly.boundedcontext.cart.app.port.CartActors;
import com.bbcc.kidly.boundedcontext.cart.in.web.CartCallerArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 장바구니 호출 주체 해석기(@CartCaller, #998)를 등록한다. 공용 WebMvcConfig(@LoginMember 등)는 바꾸지 않고 더하기만 한다.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CartWebMvcConfig implements WebMvcConfigurer {

    private final CartActors cartActors;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CartCallerArgumentResolver(cartActors));
    }
}
