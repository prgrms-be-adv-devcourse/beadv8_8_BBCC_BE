/**
 * 유즈케이스와 조회 서비스. 메서드 1개 = 트랜잭션 1개 = 애그리거트 1개 변경. 권한 확인과 이벤트 발행(저장 후)은 여기서 한다.
 * 입력 포트 인터페이스는 두지 않고, 출력 포트 인터페이스만 {@code port}에 둔다.
 */
package com.bbcc.kidly.boundedcontext.order.app;
