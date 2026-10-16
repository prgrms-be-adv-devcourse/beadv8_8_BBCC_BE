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
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final String CLOTHES = "의류";

    private final CategoryService categoryService;
    private final ProductRegistrationService productRegistrationService;
    private final ProductSaleService productSaleService;

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

    @Transactional
    public void load() {
        for (Sample sample : samples()) {
            ProductRegisterResult result = productRegistrationService.register(SELLER_ID, toRequest(sample));
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

    private ProductRegisterRequest toRequest(Sample sample) {
        Long categoryId = findLeafCategoryId(sample.categoryName());
        CategoryInputsResult inputs = categoryService.findInputs(categoryId);
        InputAttribute material = findAttribute(inputs, "MAIN_MATERIAL");
        List<AttributeInput> attributes = List.of(
            new AttributeInput(material.attributeId(), List.of(findValueId(material, "면")), null));

        List<OptionInput> options = new ArrayList<>();
        for (OptionSample option : sample.options()) {
            List<Long> valueIds = List.of(
                findValueId(findAttribute(inputs, "COLOR"), option.color()),
                findValueId(findAttribute(inputs, "SIZE"), option.size()));
            options.add(new OptionInput(valueIds, option.quantity(), null));
        }

        List<SizeSpecInput> sizeSpecs = new ArrayList<>();
        for (SizeSpecSample spec : sample.sizeSpecs()) {
            Long sizeValueId = findValueId(findAttribute(inputs, "SIZE"), spec.size());
            Map<String, BigDecimal> measurements = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : spec.measurements().entrySet()) {
                measurements.put(entry.getKey(), BigDecimal.valueOf(entry.getValue()));
            }
            sizeSpecs.add(new SizeSpecInput(sizeValueId, spec.minHeightCm(), spec.maxHeightCm(), measurements));
        }

        return new ProductRegisterRequest(sample.name(), BRAND, categoryId, null, null, List.of(), attributes,
            options, List.of(new PriceInput(List.of(), sample.price())), sizeSpecs);
    }

    // 최상위 「의류」 아래에서 이름으로 말단 카테고리를 찾는다. 카테고리 ID는 고정되지 않는다
    private Long findLeafCategoryId(String name) {
        for (CategoryTreeResult top : categoryService.findTree()) {
            if (!top.name().equals(CLOTHES)) {
                continue;
            }
            for (CategoryTreeResult leaf : top.children()) {
                if (leaf.name().equals(name)) {
                    return leaf.categoryId();
                }
            }
        }
        throw new IllegalStateException("카테고리를 찾을 수 없습니다: " + name);
    }

    private InputAttribute findAttribute(CategoryInputsResult inputs, String code) {
        for (InputAttribute attribute : inputs.attributes()) {
            if (attribute.code().equals(code)) {
                return attribute;
            }
        }
        throw new IllegalStateException("항목을 찾을 수 없습니다: " + code);
    }

    private Long findValueId(InputAttribute attribute, String value) {
        for (SelectableValue selectable : attribute.values()) {
            if (selectable.value().equals(value)) {
                return selectable.valueId();
            }
        }
        throw new IllegalStateException("목록 값을 찾을 수 없습니다: " + attribute.code() + " " + value);
    }
}
