package com.bbcc.kidly.boundedcontext.search.domain;

import static java.util.Objects.requireNonNull;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자 변경됨 이벤트로 만드는 검색 판매자다. 상품 목록에 상호명을 붙일 때 쓴다.
 * 저장된 값보다 늦게 일어난 이벤트일 때만 덮어쓰는 비교는 저장소가 한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchSeller {

    // 판매자 ID. 회원 ID와 같다
    private Long sellerId;

    private String businessName;

    // 이 값을 만든 판매자 변경됨의 발생 시각이다. 늦게 도착한 이전 이벤트를 무시하는 데 쓴다
    private LocalDateTime occurredAt;

    public record ChangeInfo(Long sellerId, String businessName, LocalDateTime occurredAt) {
    }

    public static SearchSeller create(ChangeInfo info) {
        requireNonNull(info, "info는 필수입니다");
        SearchSeller seller = new SearchSeller();
        seller.sellerId = requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        seller.businessName = requireNonNull(info.businessName(), "businessName은 필수입니다");
        seller.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        return seller;
    }
}
