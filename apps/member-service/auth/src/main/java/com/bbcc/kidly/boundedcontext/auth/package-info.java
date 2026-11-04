/**
 * 인증 컨텍스트: 카카오 로그인과 첫 로그인 가입, Access Token 발급, Refresh Token 재발급 · 재사용 감지, 토큰 무효화. DB 스키마 auth.
 * 회원은 {@code shared.member.out.MemberAccounts}로 만들고 읽는다. 회원 도메인은 {@code shared.auth.out}의 계약으로 무효화 · 연결 조회를 부른다.
 */
@NullMarked
package com.bbcc.kidly.boundedcontext.auth;

import org.jspecify.annotations.NullMarked;
