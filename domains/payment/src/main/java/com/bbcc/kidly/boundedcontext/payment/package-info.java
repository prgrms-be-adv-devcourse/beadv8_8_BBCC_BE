/**
 * 결제 도메인: 예치금 지갑, 충전, 결제, 환불. DB 스키마 payment.
 *
 * <ul>
 *   <li>{@code app}: 유스케이스 서비스, 이벤트 리스너</li>
 *   <li>{@code in}: 들어오는 요청: 컨트롤러, Kafka 리스너, 스케줄러. {@code dto} 안에 XxxResponse와 토스 웹훅 요청</li>
 *   <li>{@code domain}: 엔티티, 상태 enum, 리포지토리 인터페이스</li>
 *   <li>{@code out}: 나가는 연결: Spring Data JPA 리포지토리 구현, Kafka 전송, 토스 클라이언트, 암호화</li>
 * </ul>
 */
package com.bbcc.kidly.boundedcontext.payment;
