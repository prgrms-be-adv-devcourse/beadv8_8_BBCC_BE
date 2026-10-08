/**
 * 결제 도메인: 예치금 지갑, 충전, 결제, 환불. DB 스키마 payment.
 *
 * <ul>
 *   <li>{@code api}: 다른 도메인에 공개하는 인터페이스, DTO, 이벤트. 다른 도메인은 이 패키지만 참조할 수 있다</li>
 *   <li>{@code presentation}: 컨트롤러, {@code dto} 안에 XxxRequest / XxxResponse</li>
 *   <li>{@code application}: 서비스, 이벤트 리스너</li>
 *   <li>{@code domain}: 엔티티, 상태 enum, 리포지토리 인터페이스</li>
 *   <li>{@code infrastructure}: QueryDSL 구현, 외부 API 클라이언트</li>
 * </ul>
 */
package com.bbcc.kidly.payment;
