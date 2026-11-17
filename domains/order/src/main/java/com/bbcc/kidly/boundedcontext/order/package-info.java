/**
 * 주문 컨텍스트: 주문하기 · 결제 반영 · 취소 · 구매확정, 운영 확인(멈춘 주문 · 처리 실패 · DLT · 아웃박스), 다른 도메인 복사본.
 * 장바구니({@code boundedcontext.cart}) · 배송({@code boundedcontext.delivery}) · 조르기({@code boundedcontext.wish})는 별도 컨텍스트이고
 * 포트로만 이어진다(#617). DB 스키마 orders(order는 예약어)는 네 컨텍스트가 함께 쓴다.
 * 컨텍스트 사이 코드는 나누지 않고, 업무 규칙이 없고 상태를 공유하지 않는 공용 도구만 {@code boundedcontext.common}에
 * 둔다(주기 작업 순환 커서 {@code common.in.scheduler.RotatingCursor}, #1509).
 * 기준 문서: 기획서 + Kidly 주문 정리(하이레벨). 헥사고날 + 풍부한 도메인 모델.
 *
 * <ul>
 *   <li>{@code domain}: 엔티티 · 값 객체 · 상태 전이 · 규칙. {@code jakarta.persistence}까지만 안다</li>
 *   <li>{@code app}: 유즈케이스 · 조회 서비스. {@code port}에 출력 포트, {@code port.in}에 다른 컨텍스트에 내주는 입력 포트
 *       (구현은 {@code provided})</li>
 *   <li>{@code in}: {@code web}(API, 관리자는 {@code web.admin}, 서비스 사이 내부 API는 {@code web.internal})
 *       · {@code event}(리스너) · {@code scheduler}(시간 작업)
 *       · {@code mcp}(운영 조회 MCP 창구) · {@code config}(Kafka 리스너 · MCP · 웹 설정)</li>
 *   <li>{@code out}: {@code persistence}(Spring Data JPA 저장소만) · {@code outbox}(이벤트 아웃박스) · {@code kafka}(발송 · 봉투 ·
 *       대기량 확인) · {@code deadletter}(DLT 보관) · {@code lock}(DB 잠금) · {@code ai}(멈춘 주문 AI 진단)
 *       · {@code config}(설정값 · Kafka 토픽 · AI 제공자) · {@code cart} · {@code delivery}(다른 컨텍스트 어댑터, #651)</li>
 * </ul>
 *
 * <p>의존 방향: in → app → domain ← out, out → app/port. 규칙은 {@code OrderArchitectureTest} · {@code LayerArchitectureTest}
 * · {@code OrderContextArchitectureTest}(컨텍스트 경계) · {@code OrderPackageLayoutArchitectureTest}(컨텍스트 아래 계층 패키지)가
 * 검사한다.
 */
package com.bbcc.kidly.boundedcontext.order;
