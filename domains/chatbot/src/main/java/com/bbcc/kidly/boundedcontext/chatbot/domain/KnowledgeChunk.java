package com.bbcc.kidly.boundedcontext.chatbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 근거 문서 본문의 한 조각과 그 임베딩. 문서를 저장 · 수정할 때마다 문서의 조각을 모두 새로 만든다.
 */
@Entity
@Table(schema = "chatbot", name = "knowledge_chunk")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, columnDefinition = "real[]")
    private float[] embedding;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private KnowledgeChunk(Long documentId, int seq, String content, float[] embedding) {
        this.documentId = documentId;
        this.seq = seq;
        this.content = content;
        this.embedding = embedding;
    }

    public static KnowledgeChunk of(Long documentId, int seq, String content, float[] embedding) {
        return new KnowledgeChunk(documentId, seq, content, embedding);
    }
}
