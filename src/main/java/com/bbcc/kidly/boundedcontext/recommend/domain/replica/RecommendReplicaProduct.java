package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천이 사이즈를 계산하려고 상품 정보 변경됨 이벤트에서 옮겨 둔 상품 정보다.
 * 주문 도메인에도 상품 복사본 엔티티 {@code ReplicaProduct}가 있어서, JPA 엔티티 이름이 겹치지 않게 클래스 이름 앞에 도메인 이름을 붙인다.
 */
@Entity
@Table(schema = "recommend", name = "replica_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendReplicaProduct extends BaseTimeEntity {

    // 원래 상품 ID를 그대로 넣으므로 @GeneratedValue를 쓰지 않는다
    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    // 감사 칸 created_at과 이름이 겹치지 않게, 상품이 등록된 시각임을 이름에 드러낸다
    @Column(name = "product_registered_at", nullable = false)
    private LocalDateTime productRegisteredAt;

    @Column(name = "is_size_free", nullable = false)
    private boolean sizeFree;

    @Column(name = "event_occurred_at", nullable = false)
    private LocalDateTime eventOccurredAt;

    @ElementCollection
    @CollectionTable(schema = "recommend", name = "replica_product_size",
        joinColumns = @JoinColumn(name = "product_id"))
    private List<SizeRange> sizes = new ArrayList<>();

    public record ChangeInfo(Long productId, Long sellerId, Long categoryId, ProductStatus status,
        String thumbnailUrl, LocalDateTime registeredAt, boolean sizeFree, List<OptionInfo> options,
        LocalDateTime occurredAt) {
    }

    /**
     * 사이즈 한 줄의 입력 값이다. 권장 키는 이벤트에서 비어 있을 수 있어 Integer로 받는다.
     */
    public record OptionInfo(String size, Integer minHeightCm, Integer maxHeightCm) {
    }

    public static RecommendReplicaProduct create(ChangeInfo info) {
        RecommendReplicaProduct product = new RecommendReplicaProduct();
        product.productId = info.productId();
        product.sellerId = info.sellerId();
        product.categoryId = info.categoryId();
        product.status = info.status();
        product.thumbnailUrl = info.thumbnailUrl();
        product.productRegisteredAt = info.registeredAt();
        product.sizeFree = info.sizeFree();
        product.eventOccurredAt = info.occurredAt();
        for (OptionInfo option : info.options()) {
            product.sizes.add(SizeRange.of(option.size(), option.minHeightCm(), option.maxHeightCm()));
        }
        return product;
    }

    /**
     * 사이즈 추천 계산에 넘길 입력으로 바꾼다.
     */
    public ProductSizes toProductSizes() {
        return ProductSizes.of(sizeFree, List.copyOf(sizes));
    }
}
