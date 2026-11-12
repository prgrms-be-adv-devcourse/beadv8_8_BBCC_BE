package com.bbcc.kidly.boundedcontext.chatbot.in.web;

import com.bbcc.kidly.boundedcontext.chatbot.app.ChatService;
import com.bbcc.kidly.boundedcontext.chatbot.in.web.dto.ChatRequest;
import com.bbcc.kidly.boundedcontext.chatbot.in.web.dto.ChatResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 챗봇 API. 로그인한 회원만 묻는다. 대화는 저장하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 200. 근거 문서가 없으면 정해진 답과 빈 sources. 오류: 400 INVALID_INPUT, 503 AI_UNAVAILABLE.
     */
    @PostMapping("/messages")
    public ChatResponse send(@LoginMember AuthMember member, @Valid @RequestBody ChatRequest request) {
        return ChatResponse.from(chatService.answer(request.message(), request.historyTurns()));
    }
}
