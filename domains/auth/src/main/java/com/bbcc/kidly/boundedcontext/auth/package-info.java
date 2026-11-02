/**
 * 인증 컨텍스트: 카카오 로그인, Access Token 발급 · 검증, Refresh Token 재발급, 토큰 무효화. DB 스키마 auth.
 * 코드는 Task 3에서 회원 도메인으로부터 옮긴다. 지금은 뼈대만 있다.
 *
 * <p>어노테이션은 이 패키지 정보를 클래스로 남겨, 뼈대만 있을 때도 {@code LayerArchitectureTest}의
 * 검사 대상(DOMAIN_CLASSES_EXIST)이 비지 않게 한다(package-info만으로는 javac가 클래스 파일을 만들지 않는다).
 */
@NullMarked
package com.bbcc.kidly.boundedcontext.auth;

import org.jspecify.annotations.NullMarked;
