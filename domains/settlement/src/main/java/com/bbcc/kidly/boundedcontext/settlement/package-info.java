/**
 * 정산 도메인: 정산 대상, 월 정산, 정산 내역. DB 스키마 settlement.
 *
 * <ul>
 *   <li>{@code app}: 유스케이스 서비스, 이벤트 리스너</li>
 *   <li>{@code in}: 들어오는 요청: 컨트롤러, {@code dto} 안에 XxxRequest / XxxResponse</li>
 *   <li>{@code domain}: 엔티티, 상태 enum, 리포지토리 인터페이스, {@code event} 안에 도메인 내부 이벤트</li>
 *   <li>{@code out}: 나가는 연결: QueryDSL 구현, 외부 API 클라이언트</li>
 * </ul>
 */
package com.bbcc.kidly.boundedcontext.settlement;
