package com.bbcc.kidly.boundedcontext.chatbot.in.web.admin;

import com.bbcc.kidly.boundedcontext.chatbot.app.KnowledgeDocumentService;
import com.bbcc.kidly.boundedcontext.chatbot.in.web.dto.KnowledgeDocumentRequest;
import com.bbcc.kidly.boundedcontext.chatbot.in.web.dto.KnowledgeDocumentResponse;
import com.bbcc.kidly.boundedcontext.chatbot.in.web.dto.KnowledgeDocumentSummaryResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthAdmin;
import com.bbcc.kidly.global.security.LoginAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 챗봇 답변 근거 문서 관리. 저장 · 수정하면 본문을 조각내 임베딩한다.
 */
@RestController
@RequestMapping(AdminKnowledgeDocumentController.DOCUMENTS_PATH)
@RequiredArgsConstructor
public class AdminKnowledgeDocumentController {

    public static final String DOCUMENTS_PATH = "/api/v1/admin/chatbot/documents";

    private final KnowledgeDocumentService documentService;

    /**
     * 201 + Location. 오류: 400 INVALID_INPUT · INVALID_CATEGORY, 503 AI_UNAVAILABLE.
     */
    @PostMapping
    public ResponseEntity<KnowledgeDocumentResponse> create(@LoginAdmin AuthAdmin admin,
        @Valid @RequestBody KnowledgeDocumentRequest request) {
        KnowledgeDocumentResponse body = KnowledgeDocumentResponse.from(
            documentService.create(request.title(), request.category(), request.content()));
        return ResponseEntity.created(URI.create(DOCUMENTS_PATH + "/" + body.id())).body(body);
    }

    /**
     * 200. 최신 문서부터. page 0부터, size 1~100(기본 20).
     */
    @GetMapping
    public PageResponse<KnowledgeDocumentSummaryResponse> list(@LoginAdmin AuthAdmin admin,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(documentService.list(page, size).map(KnowledgeDocumentSummaryResponse::from));
    }

    /**
     * 200. 오류: 404 DOCUMENT_NOT_FOUND.
     */
    @GetMapping("/{id}")
    public KnowledgeDocumentResponse get(@LoginAdmin AuthAdmin admin, @PathVariable Long id) {
        return KnowledgeDocumentResponse.from(documentService.get(id));
    }

    /**
     * 200. 제목 · 분류 · 본문을 모두 바꾸고 조각을 다시 만든다.
     * 오류: 400 INVALID_INPUT · INVALID_CATEGORY, 404 DOCUMENT_NOT_FOUND, 503 AI_UNAVAILABLE.
     */
    @PutMapping("/{id}")
    public KnowledgeDocumentResponse update(@LoginAdmin AuthAdmin admin, @PathVariable Long id,
        @Valid @RequestBody KnowledgeDocumentRequest request) {
        return KnowledgeDocumentResponse.from(
            documentService.update(id, request.title(), request.category(), request.content()));
    }

    /**
     * 204. 조각도 함께 지운다. 오류: 404 DOCUMENT_NOT_FOUND.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@LoginAdmin AuthAdmin admin, @PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
