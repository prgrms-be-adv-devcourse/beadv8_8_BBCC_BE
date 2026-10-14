package com.bbcc.kidly.boundedcontext.product.domain.product;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    /**
     * 옵션을 한꺼번에 추가할 때 받는 값이다. sellerId는 요청한 판매자이고, prices는 가격 구분 묶음마다 하나씩이다.
     */
    public record AddOptionsInfo(Long sellerId, List<OptionInfo> options, List<PriceInfo> prices) {
    }

    /**
     * 옵션 하나의 값이다. 옵션 항목마다 값 하나를 넣고, skuCode는 없으면 null이다.
     */
    public record OptionInfo(List<AttributeValue> values, String skuCode) {
    }

    /**
     * 가격 구분 묶음 하나의 가격이다. values는 그 묶음의 가격 구분 항목 값이고, 가격 구분 항목이 없는 유형은 빈 목록이다.
     */
    public record PriceInfo(List<AttributeValue> values, Long price) {
    }

    /**
     * 실측과 권장 키를 한꺼번에 추가할 때 받는 값이다. sellerId는 요청한 판매자이다.
     */
    public record AddSizeSpecsInfo(Long sellerId, List<SizeSpecInfo> specs) {
    }

    /**
     * 사이즈 값 하나의 실측과 권장 키 범위다. measurements의 키는 실측 항목 코드이고 값은 cm이다.
     */
    public record SizeSpecInfo(
        AttributeValue sizeValue, Integer minHeightCm, Integer maxHeightCm, Map<String, BigDecimal> measurements) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 판매자는 다른 도메인의 데이터라서 FK 없이 ID 값으로만 참조한다
    @Column(nullable = false)
    private Long sellerId;

    // 카테고리는 같은 상품 도메인이라 FK가 맞지만, 카테고리 엔티티가 아직 없어 ID 값으로 둔다. 카테고리를 만들 때 FK로 바꾼다
    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String name;

    private String brand;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String thumbnailUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> imageUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    // 같은 상품을 동시에 바꾸면 나중 쪽이 실패한다
    @Version
    private Long version;

    // DB에서 다시 읽어도 입력한 순서를 지키려고 id 순으로 읽는다
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<ProductInfoValue> infoValues = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<ProductOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<SizeSpec> sizeSpecs = new ArrayList<>();

    /**
     * 상품을 판매대기 상태로 등록한다. 정보 값의 항목과 목록 값은 productType과 같은 영속성 컨텍스트에서 읽은 객체여야 한다.
     * 항목과 값을 객체 동일성(==)으로 비교하기 때문이다.
     */
    public static Product register(RegisterInfo info, ProductType productType) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(productType, "productType은 필수입니다");
        requireNonNull(info.infoValues(), "infoValues는 필수입니다");
        requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        requireNonNull(info.categoryId(), "categoryId는 필수입니다");
        requireNonNull(info.imageUrls(), "imageUrls는 필수입니다");
        // null 검사는 모두 업무 규칙 검사보다 앞에 둔다. null이 섞인 호출이 업무 오류(400)로 가려지지 않게 하기 위해서다
        requireNonNull(info.name(), "name은 필수입니다");
        ProductInfoValues.requireNonNullValues(info.infoValues());
        if (info.name().isBlank()) {
            throw new IllegalArgumentException("상품명은 비어 있을 수 없습니다");
        }
        ProductInfoValues.validate(info.infoValues(), productType);
        Product product = new Product();
        product.sellerId = info.sellerId();
        product.categoryId = info.categoryId();
        product.name = info.name();
        product.brand = info.brand();
        product.description = info.description();
        product.thumbnailUrl = info.thumbnailUrl();
        product.imageUrls = new ArrayList<>(info.imageUrls());
        product.status = ProductStatus.SALE_PENDING;
        product.infoValues.addAll(ProductInfoValues.create(product, info.infoValues()));
        return product;
    }

    /**
     * 옵션을 판매중 상태로 추가한다. 하나라도 잘못되면 하나도 추가하지 않는다.
     */
    public void addOptions(AddOptionsInfo info, ProductType productType) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(productType, "productType은 필수입니다");
        requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        ProductOptions.requireNonNullValues(info);
        if (info.options().isEmpty()) {
            throw new IllegalArgumentException("옵션 목록은 비어 있을 수 없습니다");
        }
        // 등록한 판매자만 옵션을 추가할 수 있다 (OPT-08)
        if (!sellerId.equals(info.sellerId())) {
            throw new BusinessException(ProductErrorCode.NOT_PRODUCT_OWNER);
        }
        for (OptionInfo option : info.options()) {
            ProductOptions.validateOption(option.values(), productType);
        }
        List<List<AttributeValue>> valueLists = ProductOptions.valueLists(info.options(), options);
        ProductOptions.validateSameAttributes(valueLists);
        ProductOptions.validateNoDuplicate(valueLists);
        ProductOptions.validateSizeFree(valueLists, productType);
        ProductOptions.validateExistingPrices(info.prices(), options, productType);
        List<ProductOption> created = new ArrayList<>();
        for (OptionInfo option : info.options()) {
            long price = ProductOptions.findPrice(option.values(), info.prices(), productType);
            List<AttributeValue> values = ProductOptions.inTypeOrder(option.values(), productType);
            created.add(ProductOption.create(this, price, option.skuCode(), values));
        }
        options.addAll(created);
    }

    /**
     * 사이즈 값마다 실측과 권장 키를 받은 순서대로 추가한다.
     */
    public void addSizeSpecs(AddSizeSpecsInfo info, Category category, ProductType productType) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(category, "category는 필수입니다");
        requireNonNull(productType, "productType은 필수입니다");
        requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        ProductSizeSpecs.requireNonNullValues(info);
        if (info.specs().isEmpty()) {
            throw new IllegalArgumentException("실측 목록은 비어 있을 수 없습니다");
        }
        // 등록한 판매자만 실측을 넣을 수 있다 (OPT-08)
        if (!sellerId.equals(info.sellerId())) {
            throw new BusinessException(ProductErrorCode.NOT_PRODUCT_OWNER);
        }
        ProductSizeSpecs.validateSizeRole(productType);
        ProductSizeSpecs.validateHeightRanges(info.specs());
        ProductSizeSpecs.validateNoDuplicate(info.specs(), sizeSpecs);
        ProductSizeSpecs.validateMeasurementItems(info.specs(), category);
        ProductSizeSpecs.validateSizeValuesInOptions(info.specs(), options, productType);
        List<SizeSpec> created = new ArrayList<>();
        for (SizeSpecInfo spec : info.specs()) {
            created.add(SizeSpec.create(this, spec));
        }
        sizeSpecs.addAll(created);
    }

    // 사이즈 무관 여부는 칸에 저장하지 않고 옵션의 사이즈 값으로 계산한다 (SIZE-02)
    public boolean isSizeFree() {
        return ProductOptions.isSizeFree(options);
    }

    public List<String> getImageUrls() {
        return Collections.unmodifiableList(imageUrls);
    }

    public List<ProductInfoValue> getInfoValues() {
        return Collections.unmodifiableList(infoValues);
    }

    public List<ProductOption> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public List<SizeSpec> getSizeSpecs() {
        return Collections.unmodifiableList(sizeSpecs);
    }
}
