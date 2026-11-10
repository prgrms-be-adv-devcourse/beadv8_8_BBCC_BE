/**
 * 활동 기록 컨텍스트: 리뷰 · 찜 · 검사 · 작성 자격에서 생긴 사실을 원래 변경과 같은 트랜잭션에서 한 행씩 남기고(append-only, 표
 * community.activity), 커밋 뒤에만 흐름 카운터를 올린다. 다른 컨텍스트는 자기 {@code out} 어댑터에서 {@code app.port.in.ActivityRecorder}를
 * 부르고, 이 컨텍스트는 다른 컨텍스트를 부르지 않는다. DB 스키마 community.
 *
 * <p>의존 방향: app → domain ← out, out → app.port. 규칙은 이 모듈의 {@code LayerArchitectureTest} ·
 * {@code CommunityContextArchitectureTest} · {@code CommunityPackageLayoutArchitectureTest}가 검사한다.
 * 어노테이션은 이 패키지 정보를 클래스로 남겨 뼈대만 있을 때도 규칙 검사 대상이 비지 않게 한다(하위 패키지에는 적용되지 않는다).
 */
@NullMarked
package com.bbcc.kidly.boundedcontext.activity;

import org.jspecify.annotations.NullMarked;
