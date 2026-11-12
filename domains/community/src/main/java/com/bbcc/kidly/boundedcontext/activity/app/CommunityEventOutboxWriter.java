package com.bbcc.kidly.boundedcontext.activity.app;

import com.bbcc.kidly.boundedcontext.activity.app.port.EventOutbox;
import com.bbcc.kidly.boundedcontext.activity.app.port.EventOutbox.NewMessage;
import com.bbcc.kidly.boundedcontext.activity.app.port.in.ActivityRecorder.FavoriteRemoved;
import com.bbcc.kidly.shared.community.event.FavoriteAddedEvent;
import com.bbcc.kidly.shared.community.event.FavoriteRemovalCause;
import com.bbcc.kidly.shared.community.event.FavoriteRemovedEvent;
import com.bbcc.kidly.shared.community.event.ReviewChangedEvent;
import com.bbcc.kidly.shared.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보낼 커뮤니티 이벤트를 아웃박스에 넣는다(부르는 쪽 트랜잭션, {@link ActivityRecordingService}가 활동 행과 함께 부른다).
 * 이벤트 record를 만들고 본문(공통 칸 7개 + data, {@link CommunityEventEnvelope})을 여기서 완성해 넣는다. 키는 이벤트의 correlationId
 * (리뷰는 리뷰 ID, 찜은 회원 ID). 닉네임 · 본문 · 사진 주소는 담지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CommunityEventOutboxWriter {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final EventOutbox outbox;

    /**
     * @param visible 지금 공개(목록 · 상품 요약에 들어감)인지
     */
    void reviewChanged(long reviewId, long orderItemId, Long productId, int rating, String fit, boolean visible,
        LocalDateTime now) {
        append(List.of(new ReviewChangedEvent(UUID.randomUUID(), now,
            new ReviewChangedEvent.Data(reviewId, orderItemId, productId, rating, fit, visible))), now);
    }

    void favoriteAdded(long favoriteId, long memberId, Long productId, LocalDateTime now) {
        append(List.of(new FavoriteAddedEvent(UUID.randomUUID(), now,
            new FavoriteAddedEvent.Data(favoriteId, memberId, productId))), now);
    }

    /**
     * @param withdrawn 회원 탈퇴로 지웠으면 true(까닭 MEMBER_WITHDRAWN), 회원이 해제했으면 false(USER)
     */
    void favoritesRemoved(List<FavoriteRemoved> removed, boolean withdrawn, LocalDateTime now) {
        FavoriteRemovalCause cause = withdrawn ? FavoriteRemovalCause.MEMBER_WITHDRAWN : FavoriteRemovalCause.USER;
        append(removed.stream()
            .map(favorite -> (DomainEvent) new FavoriteRemovedEvent(UUID.randomUUID(), now,
                new FavoriteRemovedEvent.Data(favorite.favoriteId(), favorite.memberId(), favorite.productId(), cause)))
            .toList(), now);
    }

    private void append(List<DomainEvent> events, LocalDateTime now) {
        outbox.append(events.stream()
            .map(event -> new NewMessage(event.eventId(), event.eventType(), event.correlationId(),
                jsonMapper.writeValueAsString(CommunityEventEnvelope.from(event)), now))
            .toList());
    }
}
