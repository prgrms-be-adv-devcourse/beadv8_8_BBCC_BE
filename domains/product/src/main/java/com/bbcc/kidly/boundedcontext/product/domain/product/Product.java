package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "product", name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    public record RegisterInfo(
        Long sellerId,
        Category category,
        String name,
        String brand,
        String description,
        String thumbnailUrl,
        List<String> imageUrls,
        List<InfoValueInfo> infoValues) {
    }

    /**
     * 상품 정보를 수정할 때 받는 값이다. sellerId는 요청한 판매자이고, version은 요청한 쪽이 마지막으로 읽은 상품의 version이다.
     * 나머지는 바꾼 뒤의 값이며, 바꾸지 않는 칸도 지금 값을 그대로 담아 보낸다.
     */
    public record UpdateInfo(
        Long sellerId,
        Long version,
        Category category,
        String name,
        String brand,
        String description,
        String thumbnailUrl,
        List<String> imageUrls,
        List<InfoValueInfo> infoValues) {
    }

    public record SaleInputsInfo(List<OptionInfo> options, List<PriceInfo> prices, List<SizeSpecInfo> sizeSpecs) {
    }

    /**
     * 상품을 수정한 결과다. changed는 상품이 바뀌었는지, removedOptions는 옵션을 바꿔 넣으면서 뺀 옵션,
     * requestedOptions는 받은 옵션과 같은 순서로 짝지은 옵션(남긴 옵션이거나 새로 넣은 옵션)이다.
     * 옵션 · 가격 · 실측을 받지 않았으면 두 목록은 비어 있다.
     */
    public record UpdateResult(boolean changed, List<ProductOption> removedOptions,
        List<ProductOption> requestedOptions) {
    }

    public record InfoValueInfo(Attribute attribute, List<AttributeValue> values, String text) {
    }

    public record AddOptionsInfo(Long sellerId, List<OptionInfo> options, List<PriceInfo> prices) {
    }

    public record OptionInfo(List<AttributeValue> values, String skuCode) {
    }

    /**
     * 가격 구분 묶음 하나의 가격이다. values는 그 묶음의 가격 구분 항목 값이고, 가격 구분 항목이 없는 유형은 빈 목록이다.
     */
    public record PriceInfo(List<AttributeValue> values, Long price) {
    }

    public record AddSizeSpecsInfo(Long sellerId, List<SizeSpecInfo> specs) {
    }

    public record SizeSpecInfo(
        AttributeValue sizeValue, Integer minHeightCm, Integer maxHeightCm, Map<String, BigDecimal> measurements) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sellerId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

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

    @ColumnDefault("false")
    @Column(name = "is_stopped_by_admin", nullable = false)
    private boolean stoppedByAdmin;

    @Column(name = "sale_started_at")
    private LocalDateTime saleStartedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OptimisticLock(excluded = false)
    @OrderBy("id ASC")
    private List<ProductInfoValue> infoValues = new ArrayList<>();

    // mappedBy 컬렉션은 Hibernate가 version 검사에서 빼므로, 옵션 · 실측(아래)만 추가해도 version이 오르도록 포함한다.
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OptimisticLock(excluded = false)
    @OrderBy("id ASC")
    private List<ProductOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OptimisticLock(excluded = false)
    @OrderBy("id ASC")
    private List<SizeSpec> sizeSpecs = new ArrayList<>();

    public static Product register(RegisterInfo info) {
        rejectDeletedCategory(info.category());
        ProductType productType = info.category().productTypeToRegister();
        ProductInfoValues.validate(info.infoValues(), productType);
        var product = new Product();
        product.sellerId = info.sellerId();
        product.category = info.category();
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
     * 기본 정보와 정보 값에 더해, 판매대기 상품이면 옵션 · 가격 · 실측도 받은 값으로 맞춘다. saleInputs가 null이면
     * 옵션과 실측은 그대로 둔다. 값 조합이 같은 옵션은 객체를 그대로 두고 SKU 코드와 판매가만 바꾼다.
     */
    public UpdateResult update(UpdateInfo info, SaleInputsInfo saleInputs) {
        validateOwner(info.sellerId());
        validateNotDelisted();
        boolean categoryChanged = category != info.category();
        if (categoryChanged) {
            rejectDeletedCategory(info.category());
        }
        ProductInfoValues.validate(info.infoValues(), info.category().productTypeToRegister());
        if (saleInputs != null && status != ProductStatus.SALE_PENDING) {
            throw new BusinessException(ProductErrorCode.SALE_INPUTS_LOCKED);
        }
        if (status != ProductStatus.SALE_PENDING && categoryChanged) {
            throw new BusinessException(ProductErrorCode.CATEGORY_CHANGE_NOT_ALLOWED);
        }
        SaleInputsReplacement replacement = null;
        if (saleInputs != null) {
            replacement = SaleInputsReplacement.prepare(this, saleInputs, info.category());
        } else if (categoryChanged) {
            throw new BusinessException(ProductErrorCode.SALE_INPUTS_REQUIRED_FOR_CATEGORY_CHANGE);
        }
        if (isUnchangedBy(info, replacement)) {
            List<ProductOption> requested = replacement == null ? List.of() : replacement.matchedOptions(options);
            return new UpdateResult(false, List.of(), requested);
        }
        if (!Objects.equals(info.version(), version)) {
            throw new BusinessException(ProductErrorCode.PRODUCT_VERSION_MISMATCH);
        }
        replaceInfo(info);
        if (replacement == null) {
            return new UpdateResult(true, List.of(), List.of());
        }
        return replacement.replace(options, sizeSpecs);
    }

    // 지운 카테고리는 새로 고를 수 없다(TYPE-09). 지운 카테고리에 이미 있는 상품은 그대로 팔고 고칠 수 있어야 하므로
    // 새로 등록하거나 다른 카테고리로 옮길 때만 부른다
    private static void rejectDeletedCategory(Category category) {
        if (category.isDeleted()) {
            throw new BusinessException(CategoryErrorCode.CATEGORY_DELETED);
        }
    }

    private void replaceInfo(UpdateInfo info) {
        name = info.name();
        brand = info.brand();
        description = info.description();
        thumbnailUrl = info.thumbnailUrl();
        imageUrls = new ArrayList<>(info.imageUrls());
        category = info.category();
        infoValues.clear();
        infoValues.addAll(ProductInfoValues.create(this, info.infoValues()));
    }

    private boolean isUnchangedBy(UpdateInfo info, SaleInputsReplacement replacement) {
        return isSameAs(info) && (replacement == null || !replacement.differsFrom(options, sizeSpecs));
    }

    private boolean isSameAs(UpdateInfo info) {
        return category == info.category()
            && Objects.equals(name, info.name())
            && Objects.equals(brand, info.brand())
            && Objects.equals(description, info.description())
            && Objects.equals(thumbnailUrl, info.thumbnailUrl())
            && imageUrls.equals(info.imageUrls())
            && ProductInfoValues.sameAs(this, infoValues, info.infoValues());
    }

    /**
     * 옵션을 판매중 상태로 추가하고, 새로 만든 옵션을 요청 순서대로 돌려준다. 하나라도 잘못되면 하나도 추가하지 않는다.
     */
    public List<ProductOption> addOptions(AddOptionsInfo info, ProductType productType) {
        validateOwner(info.sellerId());
        validateNotDelisted();
        ProductOptions.validateNewOptions(info.options(), info.prices(), options, productType);
        List<ProductOption> created = new ArrayList<>();
        for (OptionInfo option : info.options()) {
            created.add(ProductOptions.newOption(this, option, info.prices(), productType));
        }
        options.addAll(created);
        return Collections.unmodifiableList(created);
    }

    /**
     * 보관하지 않은 옵션의 판매가에서 가격 구분 묶음마다 가격 하나를 옵션 순서대로 만든다.
     * 가격 구분 항목이 없는 유형은 빈 값 목록 묶음 하나이고, 옵션이 없으면 빈 목록이다.
     */
    public List<PriceInfo> currentPrices(ProductType productType) {
        return ProductOptions.currentPrices(options, productType);
    }

    /**
     * 사이즈 값마다 실측과 권장 키를 받은 순서대로 추가한다. 하나라도 잘못되면 하나도 추가하지 않는다.
     */
    public void addSizeSpecs(AddSizeSpecsInfo info, Category category, ProductType productType) {
        ProductSizeSpecs.requireNonNullValues(info.specs());
        validateOwner(info.sellerId());
        validateNotDelisted();
        ProductSizeSpecs.validateNewSpecs(info.specs(), sizeSpecs, options, category, productType);
        for (SizeSpecInfo spec : info.specs()) {
            sizeSpecs.add(SizeSpec.create(this, spec));
        }
    }

    /**
     * 판매를 시작해 판매중으로 바꾸고, 상태가 바뀌었는지 돌려준다. 판매중지 상품도 판매 시작 조건을 다시 검사해
     * 판매중으로 바꾼다(판매 재개). 관리자가 판매중지한 상품은 판매자가 다시 열 수 없다.
     */
    public boolean startSale(Long sellerId, Category category, ProductType productType, LocalDateTime now) {
        validateOwner(sellerId);
        validateNotDelisted();
        if (stoppedByAdmin) {
            throw new BusinessException(ProductErrorCode.SALE_STOPPED_BY_ADMIN);
        }
        return openSale(category, productType, now);
    }

    public boolean hasStartedSale() {
        return switch (status) {
            case SALE_PENDING -> false;
            case ON_SALE, STOPPED, DELISTED -> true;
        };
    }

    public boolean isVisibleToBuyer() {
        return switch (status) {
            case SALE_PENDING -> false;
            case ON_SALE, STOPPED, DELISTED -> true;
        };
    }

    public void validateOwner(Long sellerId) {
        if (!this.sellerId.equals(sellerId)) {
            throw new BusinessException(ProductErrorCode.NOT_PRODUCT_OWNER);
        }
    }

    /** 판매자가 상품의 판매를 중지해 판매중지로 바꾸고, 상태가 바뀌었는지 돌려준다. */
    public boolean stopSale(Long sellerId) {
        validateOwner(sellerId);
        validateNotDelisted();
        return markStopped();
    }

    void validateNotDelisted() {
        if (status == ProductStatus.DELISTED) {
            throw new BusinessException(ProductErrorCode.PRODUCT_DELISTED);
        }
    }

    /** 관리자가 상품의 판매를 시작하거나 다시 열고, 판매 상태가 바뀌었는지 돌려준다. 관리자 판매중지 표시도 끈다. */
    public boolean startSaleByAdmin(Category category, ProductType productType, LocalDateTime now) {
        validateNotDelisted();
        boolean changed = openSale(category, productType, now);
        stoppedByAdmin = false;
        return changed;
    }

    /**
     * 관리자가 운영 정책을 어긴 상품을 판매중지하고, 판매 상태가 바뀌었는지 돌려준다. 판매자가 이미 판매중지한 상품이면
     * 판매 상태는 그대로라 false지만, 관리자 판매중지 표시는 켜지므로 상품은 바뀐다.
     */
    public boolean stopSaleByAdmin() {
        validateNotDelisted();
        boolean changed = markStopped();
        stoppedByAdmin = true;
        return changed;
    }

    /**
     * 판매자가 상품을 더 팔지 않겠다고 등록 취소하고, 상태가 바뀌었는지 돌려준다. 판매대기 · 판매중 · 판매중지 어느 상태에서나
     * 등록 취소할 수 있고, 관리자 판매중지 표시는 그대로 둔다. 등록 취소한 상품은 판매자가 되돌릴 수 없다.
     */
    public boolean delist(Long sellerId) {
        validateOwner(sellerId);
        if (status == ProductStatus.DELISTED) {
            return false;
        }
        status = ProductStatus.DELISTED;
        return true;
    }

    public void revertDelistByAdmin() {
        if (status != ProductStatus.DELISTED) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_DELISTED);
        }
        status = ProductStatus.STOPPED;
    }

    // DB 칸이 마이크로초까지라 이벤트 값과 저장 값을 맞추려고 마이크로초 아래는 자른다
    private boolean openSale(Category category, ProductType productType, LocalDateTime now) {
        if (status == ProductStatus.ON_SALE) {
            return false;
        }
        SaleStartConditions.requireMet(options, sizeSpecs, category, productType);
        status = ProductStatus.ON_SALE;
        if (saleStartedAt == null) {
            saleStartedAt = now.truncatedTo(ChronoUnit.MICROS);
        }
        return true;
    }

    private boolean markStopped() {
        if (status == ProductStatus.SALE_PENDING) {
            throw new BusinessException(ProductErrorCode.SALE_NOT_STARTED);
        }
        if (status == ProductStatus.STOPPED) {
            return false;
        }
        status = ProductStatus.STOPPED;
        return true;
    }

    public void validateViewer(Long sellerId) {
        if (!this.sellerId.equals(sellerId)) {
            throw new BusinessException(ProductErrorCode.PRODUCT_VIEW_FORBIDDEN);
        }
    }

    public void validateSaleStartInputs(Category category, ProductType productType) {
        SaleStartConditions.requireComplete(options, sizeSpecs, category, productType);
    }

    public Long getCategoryId() {
        return category.getId();
    }

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
