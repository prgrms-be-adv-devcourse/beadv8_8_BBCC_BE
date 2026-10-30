/**
 * 들어오는 요청: {@code web}(컨트롤러 · XxxRequest / XxxResponse), {@code event}(이벤트를 보내는 도메인마다 리스너 1개),
 * {@code scheduler}(자동 구매확정 · 배송 상태 확인 · 결제 마감 · 이벤트 아웃박스 릴레이 · 정리). app만 부르고 out은 모른다.
 */
package com.bbcc.kidly.boundedcontext.order.in;
