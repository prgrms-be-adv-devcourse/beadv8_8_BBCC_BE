package com.bbcc.kidly.boundedcontext.product.domain.search;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 정보 변경됨 이벤트에서 만드는 검색용 표의 한 행이다.
 * 상품 목록과 검색이 상품 · 옵션 표를 조인하지 않고 이 표 하나만 읽도록, 목록에 보여 줄 값을 미리 계산해 둔다.
 */
@Entity
@Table(schema = "product", name = "product_search_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSearchDocument extends BaseTimeEntity {

    // 원래 상품 ID를 그대로 넣으므로 @GeneratedValue를 쓰지 않는다
    @Id
    @Column(name = "product_id")
    private Long productId;

    // 판매할 수 있는 옵션 중 최저가다. 판매할 수 있는 옵션이 하나도 없으면 전체 옵션 중 최저가다
    @Column(name = "min_price", nullable = false)
    private Long minPrice;

    // 모든 옵션이 품절일 때만 true다. 판매중지 옵션만 재고가 남은 상품은 품절로 표시하지 않는다
    @Column(name = "is_sold_out", nullable = false)
    private boolean soldOut;

    public record ChangeInfo(Long productId, Long sellerId, String name, Long categoryId, ProductStatus status,
        String thumbnailUrl, LocalDateTime productCreatedAt, boolean sizeFree, List<OptionInfo> options,
        List<ValueInfo> infoValues, LocalDateTime occurredAt) {
    }

    public record OptionInfo(long price, boolean soldOut, OptionSaleStatus saleStatus, List<ValueInfo> values) {
    }

    /**
     * 항목 code와 고른 값 ID다. 직접 입력한 항목이면 값 ID가 null이다.
     */
    public record ValueInfo(String code, Long valueId) {
    }

    public static ProductSearchDocument create(ChangeInfo info) {
        requireNonNull(info, "info는 필수입니다");
        ProductSearchDocument document = new ProductSearchDocument();
        document.productId = requireNonNull(info.productId(), "productId는 필수입니다");
        document.minPrice = lowestPrice(info.options());
        document.soldOut = allSoldOut(info.options());
        return document;
    }

    // 판매할 수 있는 옵션은 옵션 판매 상태가 ON_SALE이고 품절이 아닌 옵션이다. 하나도 없으면 전체 옵션에서 고른다
    private static Long lowestPrice(List<OptionInfo> options) {
        Long lowestPurchasable = null;
        Long lowestAll = null;
        for (OptionInfo option : options) {
            if (lowestAll == null || option.price() < lowestAll) {
                lowestAll = option.price();
            }
            if (option.saleStatus() != OptionSaleStatus.ON_SALE || option.soldOut()) {
                continue;
            }
            if (lowestPurchasable == null || option.price() < lowestPurchasable) {
                lowestPurchasable = option.price();
            }
        }
        if (lowestPurchasable != null) {
            return lowestPurchasable;
        }
        return lowestAll;
    }

    private static boolean allSoldOut(List<OptionInfo> options) {
        for (OptionInfo option : options) {
            if (!option.soldOut()) {
                return false;
            }
        }
        return true;
    }
}
