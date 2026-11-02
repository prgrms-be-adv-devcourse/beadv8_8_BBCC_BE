/**
 * 리뷰 컨텍스트(커뮤니티 모듈의 중심): 리뷰 · 작성 자격 · 상품 복사본 · 회원 탈퇴 수신. DB 스키마 community.
 *
 * <p>의존 방향: in → app → domain ← out, out → app.port. 규칙은 이 모듈의 {@code LayerArchitectureTest} ·
 * {@code CommunityContextArchitectureTest} · {@code CommunityPackageLayoutArchitectureTest}가 검사한다.
 * 어노테이션은 이 패키지 정보를 클래스로 남겨 뼈대만 있을 때도 규칙 검사 대상이 비지 않게 한다(하위 패키지에는 적용되지 않는다).
 */
@NullMarked
package com.bbcc.kidly.boundedcontext.review;

import org.jspecify.annotations.NullMarked;
