package com.bbcc.kidly.boundedcontext.product.domain.product;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 판매자가 등록하는 상품이다. 상품 애그리거트의 루트이며, 새 상품은 판매대기 상태로 만들어진다.
 */
@Entity
@Table(schema = "product", name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    /**
     * 상품을 등록할 때 받는 값이다. infoValues는 상품 정보 항목에 입력한 값이다.
     */
    public record RegisterInfo(
        Long sellerId,
        Long categoryId,
        String name,
        String brand,
        String description,
        String thumbnailUrl,
        List<String> imageUrls,
        List<InfoValueInfo> infoValues) {
    }

    /**
     * 정보 항목 하나에 입력한 값이다. 목록 항목은 values에, 직접 입력 항목은 text에 값을 담는다.
     */
    public record InfoValueInfo(Attribute attribute, List<AttributeValue> values, String text) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 판매자는 다른 도메인의 데이터라서 FK 없이 ID 값으로만 참조한다
    @Column(nullable = false)
    private Long sellerId;

    // 카테고리도 FK 없이 ID 값으로만 참조한다
    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String name;

    private String brand;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String thumbnailUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> imageUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    // 같은 상품을 동시에 바꾸면 나중 쪽이 실패한다
    @Version
    private Long version;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<ProductInfoValue> infoValues = new ArrayList<>();

    // productType은 정보 값을 검사하는 기능에서 쓴다
    public static Product register(RegisterInfo info, ProductType productType) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(productType, "productType은 필수입니다");
        requireNonNull(info.infoValues(), "infoValues는 필수입니다");
        // null이면 NPE가 먼저 나야 하므로 빈 값 검사보다 앞에 둔다
        requireNonNull(info.name(), "name은 필수입니다");
        if (info.name().isBlank()) {
            throw new IllegalArgumentException("상품명은 비어 있을 수 없습니다");
        }
        validateInfoValues(info.infoValues());
        Product product = new Product();
        product.sellerId = requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        product.categoryId = requireNonNull(info.categoryId(), "categoryId는 필수입니다");
        product.name = info.name();
        product.brand = info.brand();
        product.description = info.description();
        product.thumbnailUrl = info.thumbnailUrl();
        product.imageUrls = new ArrayList<>(requireNonNull(info.imageUrls(), "imageUrls는 필수입니다"));
        product.status = ProductStatus.SALE_PENDING;
        addInfoValues(product, info.infoValues());
        return product;
    }

    // 값마다 한 행을 입력 순서대로 만든다
    private static void addInfoValues(Product product, List<InfoValueInfo> infoValues) {
        for (InfoValueInfo infoValue : infoValues) {
            for (AttributeValue value : infoValue.values()) {
                product.infoValues.add(ProductInfoValue.create(product, infoValue.attribute(), value));
            }
        }
    }

    private static void validateInfoValues(List<InfoValueInfo> infoValues) {
        for (InfoValueInfo infoValue : infoValues) {
            // 다중 선택 목록만 값을 여러 개 넣을 수 있다 (INFO-01)
            if (infoValue.attribute().getInputType() != AttributeInputType.MULTI_LIST
                && infoValue.values().size() > 1) {
                throw new BusinessException(ProductErrorCode.MULTIPLE_VALUES_NOT_ALLOWED);
            }
        }
    }

    public List<String> getImageUrls() {
        return Collections.unmodifiableList(imageUrls);
    }

    public List<ProductInfoValue> getInfoValues() {
        return Collections.unmodifiableList(infoValues);
    }
}
