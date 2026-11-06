package com.bbcc.kidly.boundedcontext.order.in.config;

import com.bbcc.kidly.boundedcontext.order.app.port.in.ReplicaLookup;
import com.bbcc.kidly.boundedcontext.order.in.web.OrderChildCallerArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 주문의 자녀 호출 주체 해석기(@OrderChildCaller, #1000)를 등록한다. 공용 WebMvcConfig(@LoginMember 등)는 바꾸지 않고 더하기만 한다.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class OrderWebMvcConfig implements WebMvcConfigurer {

    private final ReplicaLookup replicaLookup;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new OrderChildCallerArgumentResolver(replicaLookup));
    }
}
