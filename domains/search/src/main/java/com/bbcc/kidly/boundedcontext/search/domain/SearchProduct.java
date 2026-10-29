package com.bbcc.kidly.boundedcontext.search.domain;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 정보 변경됨 이벤트로 만드는 검색 문서다. 상품 목록 한 줄에 보여 줄 값과 거르고 정렬할 값을 미리 계산해 둔다.
 * 계산 규칙(표시 가격 · 품절 · attributes)은 상품의 검색용 표(product_search_document)와 같다.
 * 저장된 문서보다 늦게 일어난 이벤트일 때만 덮어쓰는 비교는 저장소가 한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SearchProduct {

    private Long productId;

    private String name;

    private String thumbnailUrl;

    private Long sellerId;

    private Long categoryId;

    private ProductStatus status;

    private boolean sizeFree;

    // 상품이 등록된 시각이다. 최신순 정렬에 쓴다
    private LocalDateTime productCreatedAt;

    // 이 문서를 만든 상품 정보 변경됨의 발생 시각이다. 늦게 도착한 이전 이벤트를 무시하는 데 쓴다
    private LocalDateTime occurredAt;

    // 판매할 수 있는 옵션 중 최저가다. 판매할 수 있는 옵션이 하나도 없으면 전체 옵션 중 최저가다
    private long minPrice;

    // 모든 옵션이 품절일 때만 true다. 판매중지 옵션만 재고가 남은 상품은 품절로 표시하지 않는다
    private boolean soldOut;

    // 키는 항목 code이고 값은 그 항목에서 고른 값 ID 목록(오름차순, 중복 없음)이다
    private Map<String, List<Long>> attributes;

    public record ChangeInfo(Long productId, Long sellerId, String name, Long categoryId, ProductStatus status,
        String thumbnailUrl, LocalDateTime productCreatedAt, boolean sizeFree, List<OptionInfo> options,
        List<AttributeEntryInfo> infoValues, LocalDateTime occurredAt) {
    }

    public record OptionInfo(long price, boolean soldOut, OptionSaleStatus saleStatus,
        List<AttributeEntryInfo> values) {
    }

    /**
     * 항목 code와 고른 값 ID다. 직접 입력한 항목이면 값 ID가 null이다.
     */
    public record AttributeEntryInfo(String code, Long valueId) {
    }

    public static SearchProduct create(ChangeInfo info) {
        requireNonNull(info, "info는 필수입니다");
        List<OptionInfo> options = requireNonNull(info.options(), "options는 필수입니다");
        // 표시 가격은 옵션 가격에서 고르므로 옵션이 없으면 만들 수 없다 (PRICE-05 목록 표시 가격)
        if (options.isEmpty()) {
            throw new IllegalArgumentException("옵션이 없는 상품은 검색 문서로 만들 수 없습니다");
        }
        List<AttributeEntryInfo> infoValues = requireNonNull(info.infoValues(), "infoValues는 필수입니다");
        SearchProduct product = new SearchProduct();
        product.productId = requireNonNull(info.productId(), "productId는 필수입니다");
        product.name = requireNonNull(info.name(), "name은 필수입니다");
        product.thumbnailUrl = info.thumbnailUrl();
        product.sellerId = requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        product.categoryId = requireNonNull(info.categoryId(), "categoryId는 필수입니다");
        product.status = requireNonNull(info.status(), "status는 필수입니다");
        product.sizeFree = info.sizeFree();
        product.productCreatedAt = requireNonNull(info.productCreatedAt(), "productCreatedAt은 필수입니다");
        product.occurredAt = requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        product.minPrice = lowestPrice(options);
        product.soldOut = allSoldOut(options);
        product.attributes = collectAttributes(options, infoValues);
        return product;
    }

    // 판매할 수 있는 옵션은 옵션 판매 상태가 ON_SALE이고 품절이 아닌 옵션이다.
    // 판매할 수 있는 옵션이 하나도 없으면(lowestPurchasable이 시작값 그대로면) 전체 옵션의 최저가를 쓴다.
    private static long lowestPrice(List<OptionInfo> options) {
        long lowestPurchasable = Long.MAX_VALUE;
        long lowestAll = Long.MAX_VALUE;
        for (OptionInfo option : options) {
            lowestAll = Math.min(lowestAll, option.price());
            OptionSaleStatus saleStatus = requireNonNull(option.saleStatus(), "saleStatus는 필수입니다");
            if (saleStatus == OptionSaleStatus.ON_SALE && !option.soldOut()) {
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
    private static Map<String, List<Long>> collectAttributes(
        List<OptionInfo> options, List<AttributeEntryInfo> infoValues) {
        Map<String, Set<Long>> valueIdsByCode = new HashMap<>();
        for (OptionInfo option : options) {
            addValues(valueIdsByCode, requireNonNull(option.values(), "values는 필수입니다"));
        }
        addValues(valueIdsByCode, infoValues);
        // 읽는 쪽이 getAttributes()로 받은 값을 바꾸지 못하도록 읽기 전용 목록과 Map으로 담는다
        Map<String, List<Long>> attributes = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : valueIdsByCode.entrySet()) {
            attributes.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(attributes);
    }

    private static void addValues(Map<String, Set<Long>> valueIdsByCode, List<AttributeEntryInfo> values) {
        for (AttributeEntryInfo value : values) {
            String code = requireNonNull(value.code(), "code는 필수입니다");
            // 직접 입력 항목은 고른 값 ID가 없어 값 ID로 거를 수 없으므로 넣지 않는다
            if (value.valueId() == null) {
                continue;
            }
            valueIdsByCode.computeIfAbsent(code, key -> new TreeSet<>()).add(value.valueId());
        }
    }
}
