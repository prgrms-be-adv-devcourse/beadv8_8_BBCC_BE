/**
 * 들어오는 요청: {@code web}(구매자 컨트롤러 · XxxRequest / XxxResponse, 관리자 API는 {@code web.admin}, #651),
 * {@code event}(이벤트를 보내는 도메인마다 리스너 1개), {@code config}(Kafka 리스너 팩토리 · DLT 설정),
 * {@code scheduler}(자동 구매확정 · 결제 마감 · 이벤트 아웃박스 릴레이 · 정리). app만 부르고 out은 모른다.
 */
package com.bbcc.kidly.boundedcontext.order.in;
