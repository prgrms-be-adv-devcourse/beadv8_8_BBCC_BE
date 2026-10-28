package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest.AttributeInput;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest.OptionInput;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest.PriceInput;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest.SizeSpecInput;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterResult;
import com.bbcc.kidly.boundedcontext.product.app.type.CategoryService;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryInputsResult;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryInputsResult.InputAttribute;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryInputsResult.SelectableValue;
import com.bbcc.kidly.boundedcontext.product.app.type.dto.CategoryTreeResult;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSellerRepository;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 2(초기 데이터의 테스트상점)의 샘플 상품 두 개를 등록하고 판매를 시작한다.
 * local · dev 앱을 시작할 때 러너가 상품 기준 데이터 다음에 부른다.
 * 판매 시작이 상품 정보 변경됨 이벤트를 보내므로 검색용 표와 다른 도메인의 상품 복사본이 함께 생긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSampleDataService {

    private static final Long SELLER_ID = 2L;
    private static final String BRAND = "BBCC";
    private static final String CLOTHING_CATEGORY = "의류";

    private final CategoryService categoryService;
    private final ProductRegistrationService productRegistrationService;
    private final ProductSaleService productSaleService;
    private final ProductRepository productRepository;
    private final ProductReplicaSellerRepository productReplicaSellerRepository;

    /** 옵션 하나의 색상 · 사이즈 이름과 초기 재고이다. */
    private record OptionSample(String color, String size, int quantity) {
    }

    /** 사이즈 하나의 권장 키 범위와 실측 값(코드, cm)이다. */
    private record SizeSpecSample(String size, int minHeightCm, int maxHeightCm, Map<String, Integer> measurements) {
    }

    /** 샘플 상품 하나의 값이다. 가격 구분 항목이 없으므로 가격은 하나만 둔다. */
    private record Sample(String name, String categoryName, List<OptionSample> options, long price,
                          List<SizeSpecSample> sizeSpecs) {
    }

    // 상품이 하나라도 있으면 이미 넣었거나 판매자가 등록한 것이므로 통째로 건너뛴다
    @Transactional
    public void load() {
        if (productRepository.count() > 0) {
            log.info("상품이 이미 있어 상품 샘플 데이터를 넣지 않는다");
            return;
        }
        // 등록 · 판매 시작 서비스는 판매자인지 보지 않으므로, 판매자 초기 데이터가 건너뛰어진 DB에서 상점 없는 상품이 생기지 않게 한다
        if (productReplicaSellerRepository.findById(SELLER_ID).isEmpty()) {
            log.warn("판매자 {}의 판매자 복사본이 없어 상품 샘플 데이터를 넣지 않는다", SELLER_ID);
            return;
        }
        // 관리자가 기준 데이터를 다르게 만든 DB에서도 앱이 시작되도록, 샘플이 쓰는 값이 없으면 예외 대신 경고만 남긴다.
        // 상품을 하나라도 등록한 뒤 멈추지 않게 요청을 모두 먼저 만든다
        Map<String, Long> leafCategoryIds = findLeafCategoryIds();
        List<ProductRegisterRequest> requests = new ArrayList<>();
        for (Sample sample : samples()) {
            ProductRegisterRequest request = toRequest(sample, leafCategoryIds);
            if (request == null) {
                return;
            }
            requests.add(request);
        }
        for (ProductRegisterRequest request : requests) {
            ProductRegisterResult result = productRegistrationService.register(SELLER_ID, request);
            productSaleService.changeSaleStatus(SELLER_ID, result.productId(), ProductStatus.ON_SALE);
        }
        log.info("상품 샘플 데이터를 넣었다");
    }

    private List<Sample> samples() {
        Sample top = new Sample("네이비 데일리 맨투맨", "상의",
            List.of(new OptionSample("네이비", "110", 10), new OptionSample("네이비", "120", 10)), 25_000L,
            List.of(
                new SizeSpecSample("110", 105, 115,
                    Map.of("TOTAL_LENGTH", 42, "SHOULDER_WIDTH", 30, "CHEST_WIDTH", 34, "SLEEVE_LENGTH", 38)),
                new SizeSpecSample("120", 115, 125,
                    Map.of("TOTAL_LENGTH", 45, "SHOULDER_WIDTH", 32, "CHEST_WIDTH", 36, "SLEEVE_LENGTH", 41))));
        Sample bottom = new Sample("그레이 조거 팬츠", "하의",
            List.of(new OptionSample("그레이", "120", 5)), 30_000L,
            List.of(new SizeSpecSample("120", 115, 125,
                Map.of("TOTAL_LENGTH", 66, "WAIST_WIDTH", 25, "HIP_WIDTH", 34, "THIGH_WIDTH", 20, "RISE", 21))));
        return List.of(top, bottom);
    }

    // 기준 데이터에서 샘플이 쓰는 값을 못 찾으면 경고를 남기고 null을 돌려준다
    private ProductRegisterRequest toRequest(Sample sample, Map<String, Long> leafCategoryIds) {
        Long categoryId = leafCategoryIds.get(sample.categoryName());
        if (categoryId == null) {
            warnMissing(sample.categoryName() + " 카테고리");
            return null;
        }
        CategoryInputsResult inputs = categoryService.findInputs(categoryId);
        InputAttribute material = findAttribute(inputs, "MAIN_MATERIAL");
        InputAttribute color = findAttribute(inputs, "COLOR");
        InputAttribute size = findAttribute(inputs, "SIZE");
        if (material == null || color == null || size == null) {
            return null;
        }
        Long materialValueId = findValueId(material, "면");
        List<OptionInput> options = toOptions(sample, color, size);
        List<SizeSpecInput> sizeSpecs = toSizeSpecs(sample, size);
        if (materialValueId == null || options == null || sizeSpecs == null) {
            return null;
        }
        List<AttributeInput> attributes = List.of(
            new AttributeInput(material.attributeId(), List.of(materialValueId), null));
        return new ProductRegisterRequest(sample.name(), BRAND, categoryId, null, null, List.of(), attributes,
            options, List.of(new PriceInput(List.of(), sample.price())), sizeSpecs);
    }

    private List<OptionInput> toOptions(Sample sample, InputAttribute color, InputAttribute size) {
        List<OptionInput> options = new ArrayList<>();
        for (OptionSample option : sample.options()) {
            Long colorValueId = findValueId(color, option.color());
            Long sizeValueId = findValueId(size, option.size());
            if (colorValueId == null || sizeValueId == null) {
                return null;
            }
            options.add(new OptionInput(List.of(colorValueId, sizeValueId), option.quantity(), null));
        }
        return options;
    }

    private List<SizeSpecInput> toSizeSpecs(Sample sample, InputAttribute size) {
        List<SizeSpecInput> sizeSpecs = new ArrayList<>();
        for (SizeSpecSample spec : sample.sizeSpecs()) {
            Long sizeValueId = findValueId(size, spec.size());
            if (sizeValueId == null) {
                return null;
            }
            Map<String, BigDecimal> measurements = new HashMap<>();
            for (Map.Entry<String, Integer> entry : spec.measurements().entrySet()) {
                measurements.put(entry.getKey(), BigDecimal.valueOf(entry.getValue()));
            }
            sizeSpecs.add(new SizeSpecInput(sizeValueId, spec.minHeightCm(), spec.maxHeightCm(), measurements));
        }
        return sizeSpecs;
    }

    // 최상위 「의류」 아래 말단 카테고리의 이름과 ID이다. 카테고리 ID는 고정되지 않는다.
    // 같은 이름의 중간 카테고리로 입력 항목을 조회하면 오류가 나므로 말단만 담는다
    private Map<String, Long> findLeafCategoryIds() {
        Map<String, Long> ids = new HashMap<>();
        for (CategoryTreeResult top : categoryService.findTree()) {
            if (!top.name().equals(CLOTHING_CATEGORY)) {
                continue;
            }
            for (CategoryTreeResult child : top.children()) {
                if (child.leaf()) {
                    ids.put(child.name(), child.categoryId());
                }
            }
        }
        return ids;
    }

    private InputAttribute findAttribute(CategoryInputsResult inputs, String code) {
        for (InputAttribute attribute : inputs.attributes()) {
            if (attribute.code().equals(code)) {
                return attribute;
            }
        }
        warnMissing(code + " 항목");
        return null;
    }

    private Long findValueId(InputAttribute attribute, String value) {
        for (SelectableValue selectable : attribute.values()) {
            if (selectable.value().equals(value)) {
                return selectable.valueId();
            }
        }
        warnMissing(attribute.code() + " " + value);
        return null;
    }

    private void warnMissing(String missing) {
        log.warn("기준 데이터에 {}가 없어 상품 샘플 데이터를 넣지 않는다", missing);
    }
}
