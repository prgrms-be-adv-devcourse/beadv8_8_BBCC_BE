package com.bbcc.kidly.boundedcontext.favorite.in.config;

import com.bbcc.kidly.boundedcontext.favorite.app.FavoriteCallerService;
import com.bbcc.kidly.boundedcontext.favorite.in.web.FavoriteCallerArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 찜 API의 호출자 리졸버({@code @FavoriteCaller})를 등록한다. 찜 컨텍스트 전용이고 공용 웹 설정은 건드리지 않는다.
 */
@Configuration
@RequiredArgsConstructor
public class CommunityFavoriteWebConfig implements WebMvcConfigurer {

    private final FavoriteCallerService callerService;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new FavoriteCallerArgumentResolver(callerService));
    }
}
