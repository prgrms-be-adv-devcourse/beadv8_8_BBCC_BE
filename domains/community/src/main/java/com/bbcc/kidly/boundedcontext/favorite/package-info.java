/**
 * 찜 컨텍스트: 회원의 상품 찜. 상품 정보는 review의 {@code app.port.in}으로 읽는다(자기 {@code out} 어댑터에서). DB 스키마 community.
 *
 * <p>의존 방향: in → app → domain ← out, out → app.port. 규칙은 이 모듈의 {@code LayerArchitectureTest} ·
 * {@code CommunityContextArchitectureTest} · {@code CommunityPackageLayoutArchitectureTest}가 검사한다.
 * 어노테이션은 이 패키지 정보를 클래스로 남겨 뼈대만 있을 때도 규칙 검사 대상이 비지 않게 한다(하위 패키지에는 적용되지 않는다).
 */
@NullMarked
package com.bbcc.kidly.boundedcontext.favorite;

import org.jspecify.annotations.NullMarked;
