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

    private record OptionSample(String color, String size, int quantity) {
    }

    private record SizeSpecSample(String size, int minHeightCm, int maxHeightCm, Map<String, Integer> measurements) {
    }

    private record Sample(String name, String categoryName, List<OptionSample> options, long price,
                          List<SizeSpecSample> sizeSpecs) {
    }

    @Transactional
    public void load() {
        if (productRepository.count() > 0) {
            log.info("상품이 이미 있어 상품 샘플 데이터를 넣지 않는다");
            return;
        }
        if (productReplicaSellerRepository.findById(SELLER_ID).isEmpty()) {
            log.warn("판매자 {}의 판매자 복사본이 없어 상품 샘플 데이터를 넣지 않는다", SELLER_ID);
            return;
        }
        Map<String, Long> leafCategoryIds = findLeafCategoryIds();
        var requests = new ArrayList<ProductRegisterRequest>();
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
        var top = new Sample("네이비 데일리 맨투맨", "상의",
            List.of(new OptionSample("네이비", "110", 10), new OptionSample("네이비", "120", 10)), 25_000L,
            List.of(
                new SizeSpecSample("110", 105, 115,
                    Map.of("TOTAL_LENGTH", 42, "SHOULDER_WIDTH", 30, "CHEST_WIDTH", 34, "SLEEVE_LENGTH", 38)),
                new SizeSpecSample("120", 115, 125,
                    Map.of("TOTAL_LENGTH", 45, "SHOULDER_WIDTH", 32, "CHEST_WIDTH", 36, "SLEEVE_LENGTH", 41))));
        var bottom = new Sample("그레이 조거 팬츠", "바지",
            List.of(new OptionSample("그레이", "120", 5)), 30_000L,
            List.of(new SizeSpecSample("120", 115, 125,
                Map.of("TOTAL_LENGTH", 66, "WAIST_WIDTH", 25, "HIP_WIDTH", 34, "THIGH_WIDTH", 20, "RISE", 21))));
        return List.of(top, bottom);
    }

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
        var options = new ArrayList<OptionInput>();
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
        var sizeSpecs = new ArrayList<SizeSpecInput>();
        for (SizeSpecSample spec : sample.sizeSpecs()) {
            Long sizeValueId = findValueId(size, spec.size());
            if (sizeValueId == null) {
                return null;
            }
            var measurements = new HashMap<String, BigDecimal>();
            for (Map.Entry<String, Integer> entry : spec.measurements().entrySet()) {
                measurements.put(entry.getKey(), BigDecimal.valueOf(entry.getValue()));
            }
            sizeSpecs.add(new SizeSpecInput(sizeValueId, spec.minHeightCm(), spec.maxHeightCm(), measurements));
        }
        return sizeSpecs;
    }

    private Map<String, Long> findLeafCategoryIds() {
        var ids = new HashMap<String, Long>();
        for (CategoryTreeResult top : categoryService.findTree(null)) {
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
