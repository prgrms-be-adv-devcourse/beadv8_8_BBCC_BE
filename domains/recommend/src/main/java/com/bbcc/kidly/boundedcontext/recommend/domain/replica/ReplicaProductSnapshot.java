package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 상품 복사본 하나에서 목록 계산에 쓰는 값만 담은 불변 값이다.
 * 엔티티를 요청마다 읽지 않고 메모리에 보관해 두려고, 엔티티의 판단에 필요한 값을 그대로 옮겨 담는다.
 * 옮겨 담은 뒤에는 복사본이 바뀌어도 이 값은 바뀌지 않으므로, 복사본이 바뀌면 새 값으로 갈아 끼워야 한다.
 */
public record ReplicaProductSnapshot(Long productId, Long sellerId, Long categoryId, String name,
    String thumbnailUrl, Long minPrice, ProductStatus status, String brand,
    LocalDateTime productRegisteredAt, LocalDateTime saleStartedAt, ProductSizes sizes,
    Set<String> sellableSizes, List<String> categoryPathNames, boolean girlOnly,
    LocalDateTime updatedAt) {

    public ReplicaProductSnapshot {
        requireNonNull(productId, "productId는 필수입니다");
        requireNonNull(sellableSizes, "sellableSizes는 필수입니다");
        requireNonNull(categoryPathNames, "categoryPathNames는 필수입니다");
        sellableSizes = Set.copyOf(sellableSizes);
        categoryPathNames = List.copyOf(categoryPathNames);
    }

    public static ReplicaProductSnapshot from(RecommendReplicaProduct product) {
        requireNonNull(product, "product는 필수입니다");
        List<String> pathNames = new ArrayList<>();
        for (RecommendReplicaCategoryPathEntry entry : product.getCategoryPath()) {
            pathNames.add(entry.getCategoryName());
        }
        return new ReplicaProductSnapshot(product.getProductId(), product.getSellerId(),
            product.getCategoryId(), product.getName(), product.getThumbnailUrl(), product.getMinPrice(),
            product.getStatus(), product.getBrand(), product.getProductRegisteredAt(),
            product.getSaleStartedAt(), product.toProductSizes(), product.getSellableSizes(), pathNames,
            !product.isVisibleTo(Gender.MALE), product.getUpdatedAt());
    }

    /**
     * 그 사이즈의 옵션 중 판매중이고 품절이 아닌 것이 하나라도 있는지 돌려준다.
     */
    public boolean isSellable(String size) {
        return sellableSizes.contains(size);
    }

    /**
     * 판매중이고 품절이 아닌 옵션이 하나라도 있는지 돌려준다.
     */
    public boolean hasSellableOption() {
        return !sellableSizes.isEmpty();
    }

    /**
     * 이 성별의 자녀에게 보여 줄 상품인지 돌려준다. 남아에게는 여아에게만 보여 줄 상품을 보여 주지 않는다.
     */
    public boolean isVisibleTo(Gender childGender) {
        return !(childGender == Gender.MALE && girlOnly);
    }
}
