/**
 * 애그리거트마다 하위 패키지(order · cart · delivery). 값 · enum은 주인 엔티티 파일 안에.
 * 바로 아래는 여러 애그리거트가 쓰는 시간 기록(OrderTimeEntity) · 규칙 위반 예외(OrderRuleViolationException).
 */
package com.bbcc.kidly.boundedcontext.order.domain;
