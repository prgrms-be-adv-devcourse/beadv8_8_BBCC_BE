package com.bbcc.kidly.boundedcontext.wish.in.web.dto;

import com.bbcc.kidly.boundedcontext.wish.app.dto.WishView;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 자녀 한 명의 공용 장바구니(#640). 최근에 담은 줄부터. 칸은 추가만 한다.
 *
 * @param totalAmount 구매 가능한 줄의 금액 합
 */
public record WishResponse(String childId, List<Line> items, long totalAmount) {

    /**
     * 한 줄. ID는 문자열(장바구니와 같음). 상품 복사본이 없는 줄은 상품 필드가 null.
     *
     * @param addedBy 담은 사람 종류(PARENT · CHILD)
     * @param addedAt 처음 담은 시각
     * @param saleStatus 상품의 지금 판매 상태 값(ON_SALE · SOLD_OUT · STOPPED 등, #701). 상품 정보가 없으면 null
     * @param requestId 이 줄이 든 진행 중인 조르기 ID(#701). 없으면 null
     */
    public record Line(
        String wishItemId,
        String childId,
        String optionId,
        String productId,
        String sellerId,
        String productName,
        String optionName,
        String size,
        int quantity,
        Long unitPrice,
        boolean purchasable,
        long lineAmount,
        String sellerName,
        String addedBy,
        LocalDateTime addedAt,
        String saleStatus,
        String requestId) {

        public static Line from(WishView.Line view) {
            return new Line(
                toText(view.wishItemId()),
                toText(view.childId()),
                toText(view.optionId()),
                toText(view.productId()),
                toText(view.sellerId()),
                view.productName(),
                view.optionName(),
                view.size(),
                view.quantity(),
                view.unitPrice(),
                view.purchasable(),
                view.lineAmount(),
                view.sellerName(),
                view.addedBy(),
                view.addedAt(),
                view.saleStatus(),
                toText(view.requestId()));
        }
    }

    public static WishResponse from(WishView view) {
        return new WishResponse(toText(view.childId()), view.items().stream().map(Line::from).toList(),
            view.totalAmount());
    }

    private static String toText(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
