package com.bbcc.kidly.boundedcontext.recommend.domain.replica;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * 추천이 사이즈를 계산하려고 상품 정보 변경됨 이벤트에서 옮겨 둔 상품 정보다.
 * 주문 도메인에도 상품 복사본 엔티티 {@code ReplicaProduct}가 있어서, JPA 엔티티 이름이 겹치지 않게 클래스 이름 앞에 도메인 이름을 붙인다.
 */
@Entity
@Table(schema = "recommend", name = "replica_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendReplicaProduct extends BaseTimeEntity {

    // 여러 상품의 사이즈 줄과 판매 가능 사이즈를 읽을 때 상품마다 조회하지 않고 이 개수씩 묶어서 읽는다
    private static final int BATCH_FETCH_SIZE = 100;

    // 사이즈가 없는 옵션은 사이즈 무관 옵션과 같이 이 사이즈로 판매 가능 사이즈에 담는다
    private static final String FREE_SIZE = "FREE";

    // 원래 상품 ID를 그대로 넣으므로 @GeneratedValue를 쓰지 않는다
    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(nullable = false)
    private String name;

    // 옵션 판매가의 최솟값이다. 옵션이 없으면 null이다
    @Column(name = "min_price")
    private Long minPrice;

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

    // 사이즈 줄은 applyIfNewer로만 바꾸도록 getter를 두지 않고, 읽을 때는 toProductSizes를 쓴다
    @Getter(AccessLevel.NONE)
    @BatchSize(size = BATCH_FETCH_SIZE)
    @ElementCollection
    @CollectionTable(schema = "recommend", name = "replica_product_size",
        joinColumns = @JoinColumn(name = "product_id"))
    private List<SizeRange> sizes = new ArrayList<>();

    // 판매중이고 품절이 아닌 옵션의 사이즈다. 권장 키가 없는 사이즈 무관(FREE)도 담는다
    @Getter(AccessLevel.NONE)
    @BatchSize(size = BATCH_FETCH_SIZE)
    @ElementCollection
    @CollectionTable(schema = "recommend", name = "replica_product_sellable_size",
        joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "size", nullable = false, length = 20)
    private Set<String> sellableSizes = new HashSet<>();

    public record ChangeInfo(Long productId, Long sellerId, Long categoryId, String name, ProductStatus status,
        String thumbnailUrl, LocalDateTime registeredAt, boolean sizeFree, List<OptionInfo> options,
        LocalDateTime occurredAt) {
    }

    /**
     * 옵션 하나의 입력 값이다. 권장 키는 이벤트에서 비어 있을 수 있어 Integer로 받는다.
     */
    public record OptionInfo(String size, Integer minHeightCm, Integer maxHeightCm, long price, boolean soldOut,
        OptionSaleStatus saleStatus) {
    }

    public static RecommendReplicaProduct create(ChangeInfo info) {
        requireValues(info);
        RecommendReplicaProduct product = new RecommendReplicaProduct();
        product.productId = requireNonNull(info.productId(), "productId는 필수입니다");
        product.apply(info);
        return product;
    }

    /**
     * 저장된 시각보다 늦게 일어난 상품 변경만 반영한다. 같은 이벤트를 다시 받거나 이전 이벤트가 늦게 도착해도 값이 되돌아가지 않는다.
     * 반영할 때는 상품 ID를 뺀 모든 값을 덮어쓰고, 사이즈 줄도 새 옵션으로 다시 채운다.
     */
    public void applyIfNewer(ChangeInfo info) {
        requireValues(info);
        if (!info.occurredAt().isAfter(eventOccurredAt)) {
            return;
        }
        apply(info);
    }

    // 값을 하나도 바꾸기 전에 옵션의 사이즈까지 검사해서, 실패하면 복사본이 그대로 남도록 한다
    private static void requireValues(ChangeInfo info) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        requireNonNull(info.status(), "status는 필수입니다");
        requireNonNull(info.registeredAt(), "registeredAt은 필수입니다");
        requireNonNull(info.options(), "options는 필수입니다");
        requireNonNull(info.occurredAt(), "occurredAt은 필수입니다");
        for (OptionInfo option : info.options()) {
            requireNonNull(option, "option은 필수입니다");
        }
    }

    private void apply(ChangeInfo info) {
        this.sellerId = info.sellerId();
        this.categoryId = info.categoryId();
        this.name = info.name();
        this.minPrice = lowestPrice(info.options());
        this.status = info.status();
        this.thumbnailUrl = info.thumbnailUrl();
        this.productRegisteredAt = info.registeredAt();
        this.sizeFree = info.sizeFree() || hasOnlyOptionsWithoutSize(info.options());
        this.eventOccurredAt = info.occurredAt();
        // 컬렉션 객체를 새로 갈아 끼우지 않고 비운 뒤 다시 채운다
        this.sizes.clear();
        this.sellableSizes.clear();
        for (OptionInfo option : info.options()) {
            if (option.saleStatus() == OptionSaleStatus.ON_SALE && !option.soldOut()) {
                this.sellableSizes.add(option.size() == null ? FREE_SIZE : option.size());
            }
        }
        Set<String> seen = new HashSet<>();
        for (OptionInfo option : info.options()) {
            // 사이즈나 권장 키가 하나라도 없으면 아이 키로 고를 수 없으므로 사이즈 추천 후보로 두지 않는다
            if (option.size() == null || option.minHeightCm() == null || option.maxHeightCm() == null) {
                continue;
            }
            // 색상만 다른 같은 사이즈 옵션은 권장 키가 같아야 하므로, 처음 들어온 옵션의 권장 키만 쓴다
            if (!seen.add(option.size())) {
                continue;
            }
            this.sizes.add(SizeRange.of(option.size(), option.minHeightCm(), option.maxHeightCm()));
        }
    }

    /**
     * 그 사이즈의 옵션 중 판매중이고 품절이 아닌 것이 하나라도 있는지 돌려준다.
     */
    public boolean isSellable(String size) {
        return sellableSizes.contains(size);
    }

    /**
     * 판매중이고 품절이 아닌 옵션이 하나라도 있는지 돌려준다. 사이즈 무관 상품은 사이즈가 없어 이 값으로 판매 가능을 판단한다.
     */
    public boolean hasSellableOption() {
        return !sellableSizes.isEmpty();
    }

    // 사이즈 역할 항목이 없는 유형의 상품은 옵션 사이즈가 모두 null로 온다. 고객에게는 사이즈 무관 상품과 같으므로 사이즈 무관으로 본다
    private static boolean hasOnlyOptionsWithoutSize(List<OptionInfo> options) {
        if (options.isEmpty()) {
            return false;
        }
        for (OptionInfo option : options) {
            if (option.size() != null) {
                return false;
            }
        }
        return true;
    }

    private static Long lowestPrice(List<OptionInfo> options) {
        Long lowest = null;
        for (OptionInfo option : options) {
            if (lowest == null || option.price() < lowest) {
                lowest = option.price();
            }
        }
        return lowest;
    }

    /**
     * 사이즈 추천 계산에 넘길 입력으로 바꾼다.
     */
    public ProductSizes toProductSizes() {
        return ProductSizes.of(sizeFree, List.copyOf(sizes));
    }
}
