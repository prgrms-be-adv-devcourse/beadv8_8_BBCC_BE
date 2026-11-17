package com.bbcc.kidly.boundedcontext.favorite.in.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 찜 API를 부른 주체({@code FavoriteActor}). 회원 토큰이면 회원 본인, 자녀 토큰이면 자녀 계정이다
 * ({@link FavoriteCallerArgumentResolver}). 찜 컨텍스트 안에서만 쓴다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface FavoriteCaller {
}
