package com.bbcc.kidly.boundedcontext.recommend;

import static com.bbcc.kidly.boundedcontext.recommend.RecommendFixture.PRODUCT_A_SELLER_ID;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSeller;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 판매자 복사본 테스트에 쓰는 판매자는 상품 A를 파는 판매자({@code PRODUCT_A_SELLER_ID})다.
 * 맞춤 목록에서 상품 A에 이 판매자의 상호명을 붙일 수 있게 상품 A 복사본의 판매자 ID와 맞춘다.
 */
final class RecommendReplicaSellerFixture {

    static final String BUSINESS_NAME = "소소한옷장";

    /**
     * 판매자 정보 변경 이벤트가 일어난 시각. 늦고 이른 이벤트는 이 시각에서 분을 더하거나 빼서 만든다.
     */
    static final LocalDateTime SELLER_CHANGED_AT = LocalDateTime.of(2026, 10, 15, 11, 0);

    private RecommendReplicaSellerFixture() {
    }

    /**
     * 판매자가 승인되었을 때의 판매자 정보 변경이다.
     */
    static RecommendReplicaSeller.ChangeInfo sellerApproved() {
        return sellerChangeAt(BUSINESS_NAME, SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    /**
     * 상호명 · 상태 · 발생 시각만 바꾼 판매자 정보 변경이다. 어느 변경이 반영됐는지 상호명과 상태로 구분한다.
     */
    static RecommendReplicaSeller.ChangeInfo sellerChangeAt(String businessName, SellerStatus status,
        LocalDateTime occurredAt) {
        return new RecommendReplicaSeller.ChangeInfo(PRODUCT_A_SELLER_ID, businessName, status, occurredAt);
    }

    /**
     * 판매자 도메인이 판매자를 승인할 때 보내는 판매자 정보 변경됨이다. 값은 {@link #sellerApproved()}와 같다.
     */
    static SellerChangedEvent sellerApprovedEvent() {
        return sellerChangedEventAt(BUSINESS_NAME, SellerStatus.ACTIVE, SELLER_CHANGED_AT);
    }

    /**
     * 상호명 · 상태 · 발생 시각만 바꾼 판매자 정보 변경됨이다. 이벤트 ID는 새로 만든다.
     */
    static SellerChangedEvent sellerChangedEventAt(String businessName, SellerStatus status,
        LocalDateTime occurredAt) {
        return new SellerChangedEvent(UUID.randomUUID(), occurredAt,
            new SellerChangedEvent.Data(PRODUCT_A_SELLER_ID, businessName, status));
    }
}
