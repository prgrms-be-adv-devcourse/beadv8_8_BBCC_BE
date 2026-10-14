package com.bbcc.kidly.boundedcontext.product.domain.product;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAllowedValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeAttribute;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    public static Product register(RegisterInfo info, ProductType productType) {
        requireNonNull(info, "info는 필수입니다");
        requireNonNull(productType, "productType은 필수입니다");
        requireNonNull(info.infoValues(), "infoValues는 필수입니다");
        requireNonNull(info.sellerId(), "sellerId는 필수입니다");
        requireNonNull(info.categoryId(), "categoryId는 필수입니다");
        requireNonNull(info.imageUrls(), "imageUrls는 필수입니다");
        // null 검사는 모두 업무 규칙 검사보다 앞에 둔다. null이 섞인 호출이 업무 오류(400)로 가려지지 않게 하기 위해서다
        requireNonNull(info.name(), "name은 필수입니다");
        requireNonNullInfoValues(info.infoValues());
        if (info.name().isBlank()) {
            throw new IllegalArgumentException("상품명은 비어 있을 수 없습니다");
        }
        validateInfoAttributes(info.infoValues(), productType);
        validateInputTypes(info.infoValues());
        validateInfoValues(info.infoValues());
        validateAllowedInfoValues(info.infoValues(), productType);
        validateRequiredInfoValues(info.infoValues(), productType);
        Product product = new Product();
        product.sellerId = info.sellerId();
        product.categoryId = info.categoryId();
        product.name = info.name();
        product.brand = info.brand();
        product.description = info.description();
        product.thumbnailUrl = info.thumbnailUrl();
        product.imageUrls = new ArrayList<>(info.imageUrls());
        product.status = ProductStatus.SALE_PENDING;
        addInfoValues(product, info.infoValues());
        return product;
    }

    // 정보 값의 항목과 값 목록, 목록 안의 값은 null이면 안 된다. 직접 입력한 글(text)은 null을 허용한다
    private static void requireNonNullInfoValues(List<InfoValueInfo> infoValues) {
        for (InfoValueInfo infoValue : infoValues) {
            requireNonNull(infoValue, "infoValue는 필수입니다");
            requireNonNull(infoValue.attribute(), "attribute는 필수입니다");
            requireNonNull(infoValue.values(), "values는 필수입니다");
            for (AttributeValue value : infoValue.values()) {
                requireNonNull(value, "value는 필수입니다");
            }
        }
    }

    // 목록 값마다 한 행을, 직접 입력한 글이 있으면 그 항목의 한 행을 입력 순서대로 만든다
    private static void addInfoValues(Product product, List<InfoValueInfo> infoValues) {
        for (InfoValueInfo infoValue : infoValues) {
            for (AttributeValue value : infoValue.values()) {
                product.infoValues.add(ProductInfoValue.create(product, infoValue.attribute(), value));
            }
            if (hasText(infoValue.text())) {
                product.infoValues.add(
                    ProductInfoValue.createText(product, infoValue.attribute(), infoValue.text()));
            }
        }
    }

    // 값을 넣은 항목은 이 유형에 정보 항목으로 연결돼 있어야 한다. 유형에 없는 항목이나 옵션 항목은 거절한다 (INFO-03)
    private static void validateInfoAttributes(List<InfoValueInfo> infoValues, ProductType productType) {
        for (InfoValueInfo infoValue : infoValues) {
            ProductTypeAttribute link = findLink(productType, infoValue.attribute());
            if (link == null || link.getRole() != AttributeRole.INFO) {
                throw new BusinessException(ProductErrorCode.NOT_INFO_ATTRIBUTE);
            }
        }
    }

    // 직접 입력 항목은 글자만, 그 밖의 항목(팔레트 · 목록 · 다중 선택 목록)은 목록 값만 받는다
    private static void validateInputTypes(List<InfoValueInfo> infoValues) {
        for (InfoValueInfo infoValue : infoValues) {
            boolean isText = infoValue.attribute().getInputType() == AttributeInputType.TEXT;
            if (isText && !infoValue.values().isEmpty()) {
                throw new BusinessException(ProductErrorCode.INPUT_TYPE_MISMATCH);
            }
            if (!isText && hasText(infoValue.text())) {
                throw new BusinessException(ProductErrorCode.INPUT_TYPE_MISMATCH);
            }
        }
    }

    private static void validateInfoValues(List<InfoValueInfo> infoValues) {
        Set<Attribute> attributes = new HashSet<>();
        for (InfoValueInfo infoValue : infoValues) {
            // 같은 항목을 InfoValueInfo 여러 개로 나눠 보내면 거절한다 (INFO-01)
            if (!attributes.add(infoValue.attribute())) {
                throw new BusinessException(ProductErrorCode.DUPLICATE_INFO_VALUE);
            }
            // 다중 선택 목록만 값을 여러 개 넣을 수 있다 (INFO-01)
            if (infoValue.attribute().getInputType() != AttributeInputType.MULTI_LIST
                && infoValue.values().size() > 1) {
                throw new BusinessException(ProductErrorCode.MULTIPLE_VALUES_NOT_ALLOWED);
            }
            // 한 항목 안에 같은 값이 두 번 들어오면 거절한다 (INFO-01)
            if (new HashSet<>(infoValue.values()).size() != infoValue.values().size()) {
                throw new BusinessException(ProductErrorCode.DUPLICATE_INFO_VALUE);
            }
        }
    }

    // 목록 값은 그 항목의 값이어야 하고, 유형이 모든 값 허용이 아닌 항목이면 유형이 허용한 값이어야 한다 (INFO-03)
    private static void validateAllowedInfoValues(List<InfoValueInfo> infoValues, ProductType productType) {
        for (InfoValueInfo infoValue : infoValues) {
            ProductTypeAttribute link = findLink(productType, infoValue.attribute());
            for (AttributeValue value : infoValue.values()) {
                if (value.getAttribute() != infoValue.attribute()) {
                    throw new BusinessException(ProductErrorCode.INFO_VALUE_NOT_ALLOWED);
                }
                if (!link.isAllValuesAllowed() && !isAllowedByType(productType, value)) {
                    throw new BusinessException(ProductErrorCode.INFO_VALUE_NOT_ALLOWED);
                }
            }
        }
    }

    // 유형에서 그 항목의 연결을 찾는다. 없으면 null이다. 정보 값의 항목은 validateInfoAttributes가 먼저 확인한다
    private static ProductTypeAttribute findLink(ProductType productType, Attribute attribute) {
        for (ProductTypeAttribute link : productType.getAttributes()) {
            if (link.getAttribute() == attribute) {
                return link;
            }
        }
        return null;
    }

    private static boolean isAllowedByType(ProductType productType, AttributeValue value) {
        for (ProductTypeAllowedValue allowed : productType.getAllowedValues()) {
            if (allowed.getAttributeValue() == value) {
                return true;
            }
        }
        return false;
    }

    // 유형이 필수로 정한 정보 항목마다 값이 있어야 한다 (INFO-02)
    private static void validateRequiredInfoValues(List<InfoValueInfo> infoValues, ProductType productType) {
        for (ProductTypeAttribute link : productType.getAttributes()) {
            if (link.getRole() == AttributeRole.INFO
                && link.isRequired()
                && !hasValue(infoValues, link.getAttribute())) {
                throw new BusinessException(ProductErrorCode.REQUIRED_INFO_MISSING);
            }
        }
    }

    // 목록 항목은 values가, 직접 입력 항목은 text가 비어 있지 않아야 값이 있는 것이다
    private static boolean hasValue(List<InfoValueInfo> infoValues, Attribute attribute) {
        for (InfoValueInfo infoValue : infoValues) {
            if (infoValue.attribute() != attribute) {
                continue;
            }
            return !infoValue.values().isEmpty() || hasText(infoValue.text());
        }
        return false;
    }

    // 직접 입력한 글이 있는지 판단하는 기준이다. 값 저장과 필수 항목 검사가 같은 기준을 쓴다
    private static boolean hasText(String text) {
        return text != null && !text.isEmpty();
    }

    public List<String> getImageUrls() {
        return Collections.unmodifiableList(imageUrls);
    }

    public List<ProductInfoValue> getInfoValues() {
        return Collections.unmodifiableList(infoValues);
    }
}
