package com.bbcc.kidly.boundedcontext.chatbot.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 챗봇 답변의 근거 문서. 관리자가 올린 제목 · 본문을 회원 질문에 맞춰 찾아 답에 쓴다.
 */
@Entity
@Table(schema = "chatbot", name = "knowledge_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeDocument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KnowledgeCategory category;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    private KnowledgeDocument(String title, KnowledgeCategory category, String content) {
        this.title = title;
        this.category = category;
        this.content = content;
    }

    public static KnowledgeDocument create(String title, KnowledgeCategory category, String content) {
        return new KnowledgeDocument(title, category, content);
    }

    public void revise(String title, KnowledgeCategory category, String content) {
        this.title = title;
        this.category = category;
        this.content = content;
    }
}
