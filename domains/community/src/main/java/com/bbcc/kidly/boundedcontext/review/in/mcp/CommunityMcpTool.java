package com.bbcc.kidly.boundedcontext.review.in.mcp;

import java.util.Map;

/**
 * MCP 도구 하나의 정의: 이름 · 설명 · 입력 스키마(JSON Schema 맵) · 호출(인자 해석 → app 조회 → 응답 변환). MCP SDK를 모른다.
 * 응답에는 집계 · ID · 상태 · 분류만 담는다(리뷰 본문 · 사진 주소 · 닉네임 · 회원 ID · DLT 원본 · 예외 메시지 없음).
 * 응답 값은 문자열 · 숫자 · 참거짓 · null · 목록 · 맵만 쓴다(시각은 ISO 문자열).
 */
public interface CommunityMcpTool {

    String name();

    String description();

    Map<String, Object> inputSchema();

    /**
     * @throws McpToolArgumentException 인자가 틀렸을 때(도구 오류로 돌려준다)
     */
    Map<String, Object> call(McpToolArguments arguments);
}
