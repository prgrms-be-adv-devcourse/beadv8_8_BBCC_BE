/**
 * 들어오는 요청: {@code web}(구매자 컨트롤러 · XxxRequest / XxxResponse, 관리자 API는 {@code web.admin}, #651.
 * 서비스 사이 내부 API(/internal/**, 회원 최근 주문 · 판매자 주문상품, Gateway가 밖에서 막는다)는 {@code web.internal}),
 * {@code event}(이벤트를 보내는 도메인마다 리스너), {@code config}(Kafka 리스너 팩토리 · DLT · MCP · 웹 설정),
 * {@code scheduler}(자동 구매확정 · 결제 마감 · 이벤트 아웃박스 릴레이 · 정리 · 멈춘 주문 AI 진단),
 * {@code mcp}(운영 조회 MCP 창구, 읽기 전용). app만 부르고 out은 모른다.
 */
package com.bbcc.kidly.boundedcontext.order.in;
