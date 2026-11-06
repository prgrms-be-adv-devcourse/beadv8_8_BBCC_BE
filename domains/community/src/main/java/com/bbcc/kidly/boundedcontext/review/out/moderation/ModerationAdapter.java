package com.bbcc.kidly.boundedcontext.review.out.moderation;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;

/**
 * 검사기 하나. 설정(kidly.community.moderation.moderators)의 이름으로 골라 ChainedContentModerator가 순서대로 부른다.
 * 새 제공자는 이 인터페이스를 구현한 어댑터 하나를 더하고 설정에 이름을 넣는다. 예외를 던지지 않는다(실패는 UNDECIDED).
 */
public interface ModerationAdapter {

    String name();

    Result moderate(Content content);

    /**
     * 쓸 수 있는지(예: 키가 없으면 false). 쓸 수 없으면 목록에 있어도 빼고 기동 때 unavailableNotice를 한 줄 남긴다.
     */
    default boolean available() {
        return true;
    }

    default String unavailableNotice() {
        return "리뷰 검사기 꺼짐: " + name();
    }
}
