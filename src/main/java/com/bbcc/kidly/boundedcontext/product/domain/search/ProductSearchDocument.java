package com.bbcc.kidly.boundedcontext.product.domain.search;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(nullable = false)
    private String name;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(name = "is_size_free", nullable = false)
    private boolean sizeFree;

    // 감사 칸 created_at과 이름이 겹치지 않게, 상품이 등록된 시각임을 이름에 드러낸다
    @Column(name = "product_created_at", nullable = false)
    private LocalDateTime productCreatedAt;

    // 마지막으로 반영한 상품 정보 변경됨의 발생 시각이다. 늦게 도착한 이전 이벤트를 무시하는 비교는 저장 SQL이 한다
    @Column(name = "event_occurred_at", nullable = false)
    private LocalDateTime eventOccurredAt;

    // 판매할 수 있는 옵션 중 최저가다. 판매할 수 있는 옵션이 하나도 없으면 전체 옵션 중 최저가다
    @Column(name = "min_price", nullable = false)
    private Long minPrice;

    // 모든 옵션이 품절일 때만 true다. 판매중지 옵션만 재고가 남은 상품은 품절로 표시하지 않는다
    @Column(name = "is_sold_out", nullable = false)
    private boolean soldOut;

    // 키는 항목 code이고 값은 그 항목에서 고른 값 ID 목록(오름차순, 중복 없음)이다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, List<Long>> attributes;

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
        document.name = requireNonNull(info.name(), "name은 필수입니다");
        document.thumbnailUrl = info.thumbnailUrl();
        document.sellerId = requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        document.categoryId = requireNonNull(info.categoryId(), "categoryId는 필수입니다");
        document.status = requireNonNull(info.status(), "status는 필수입니다");
        document.sizeFree = info.sizeFree();
        document.productCreatedAt = requireNonNull(info.productCreatedAt(), "productCreatedAt은 필수입니다");
        document.eventOccurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        List<OptionInfo> options = requireNonNull(info.options(), "options는 필수입니다");
        // 표시 가격은 옵션 가격에서 고르므로 옵션이 없으면 만들 수 없다 (PRICE-05 목록 표시 가격)
        if (options.isEmpty()) {
            throw new IllegalArgumentException("옵션이 없는 상품은 검색용 표에 넣을 수 없습니다");
        }
        requireNonNull(info.infoValues(), "infoValues는 필수입니다");
        document.minPrice = lowestPrice(options);
        document.soldOut = allSoldOut(options);
        document.attributes = collectAttributes(info);
        return document;
    }

    // 판매할 수 있는 옵션은 옵션 판매 상태가 ON_SALE이고 품절이 아닌 옵션이다.
    // 판매할 수 있는 옵션이 하나도 없으면(lowestPurchasable이 시작값 그대로면) 전체 옵션의 최저가를 쓴다.
    private static Long lowestPrice(List<OptionInfo> options) {
        long lowestPurchasable = Long.MAX_VALUE;
        long lowestAll = Long.MAX_VALUE;
        for (OptionInfo option : options) {
            lowestAll = Math.min(lowestAll, option.price());
            if (option.saleStatus() == OptionSaleStatus.ON_SALE && !option.soldOut()) {
                lowestPurchasable = Math.min(lowestPurchasable, option.price());
            }
        }
        if (lowestPurchasable != Long.MAX_VALUE) {
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

    // 판매중지 · 품절 옵션의 값도 검색 조건으로 쓸 수 있도록 옵션 상태로 거르지 않는다
    private static Map<String, List<Long>> collectAttributes(ChangeInfo info) {
        Map<String, Set<Long>> valueIdsByCode = new HashMap<>();
        for (OptionInfo option : info.options()) {
            addValues(valueIdsByCode, option.values());
        }
        addValues(valueIdsByCode, info.infoValues());
        Map<String, List<Long>> attributes = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : valueIdsByCode.entrySet()) {
            attributes.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return attributes;
    }

    private static void addValues(Map<String, Set<Long>> valueIdsByCode, List<ValueInfo> values) {
        for (ValueInfo value : values) {
            // 직접 입력 항목은 고른 값 ID가 없어 값 ID로 거를 수 없으므로 넣지 않는다
            if (value.valueId() == null) {
                continue;
            }
            valueIdsByCode.computeIfAbsent(value.code(), code -> new TreeSet<>()).add(value.valueId());
        }
    }
}
