/**
 * 주문 도메인: 장바구니, 주문, 배송, 구매확정. DB 스키마 orders (order는 예약어).
 * 기준 문서: 기획서 v1.0 + Kidly 주문 정리(하이레벨). 헥사고날 + 풍부한 도메인 모델.
 *
 * <ul>
 *   <li>{@code domain}: 엔티티 · 값 객체 · 상태 전이 · 규칙. {@code jakarta.persistence}까지만 안다</li>
 *   <li>{@code app}: 유즈케이스(상태 변경 = 클래스 1개) · QueryService, {@code port} 안에 출력 포트 인터페이스</li>
 *   <li>{@code in}: {@code web}(API) · {@code event}(리스너 + 매퍼) · {@code scheduler}(시간 작업)</li>
 *   <li>{@code out}: {@code persistence}(JPA · 복사본 · Reader 어댑터) · {@code event}(발행) · {@code tracker}(배송 조회)</li>
 * </ul>
 *
 * <p>의존 방향: in → app → domain ← out, out → app/port. 규칙은 {@code OrderArchitectureTest}가 검사한다.
 */
package com.bbcc.kidly.boundedcontext.order;
