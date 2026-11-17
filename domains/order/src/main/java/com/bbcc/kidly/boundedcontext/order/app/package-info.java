/**
 * 유즈케이스와 조회 서비스. 상태를 바꾸는 메서드 1개 = 트랜잭션 1개가 원칙이고, 결제 완료처럼 함께 바뀌어야 하는 것(배송 생성 ·
 * 장바구니 정리)은 같은 트랜잭션에서 포트로 바꾼다. 권한 확인과 이벤트 발행(같은 트랜잭션의 아웃박스 저장)은 여기서 한다.
 * 출력 포트는 {@code port}, 다른 컨텍스트(장바구니 · 배송 · 조르기)에 내주는 입력 포트는 {@code port.in}(구현은 {@code provided})에 둔다.
 * 하위 패키지: {@code order}(주문 유즈케이스 · 조회) · {@code replica}(복사본 반영) · {@code stuck}(멈춘 주문 · AI 진단)
 * · {@code deadletter}(DLT 보관 · 재처리) · {@code outbox}(아웃박스 릴레이 · 관리자 재시도) · {@code config}(설정값).
 */
package com.bbcc.kidly.boundedcontext.order.app;
