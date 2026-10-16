package com.bbcc.kidly.boundedcontext.product.app.type;

import com.bbcc.kidly.boundedcontext.product.domain.type.Attribute;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeInputType;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRole;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItem;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품의 기준 데이터를 넣는다. local · dev 앱을 시작할 때 러너가 부른다.
 * 항목(색상 · 사이즈 · 주요소재 · 혼용률)과 목록 값, 상품 유형 「의류」, 실측 항목, 카테고리(의류 · 잡화와 그 아래 말단 카테고리)를 만든다.
 * 기준 데이터는 다른 도메인으로 복사되지 않으므로 이벤트를 내지 않는다.
 * ID를 고정하지 않으므로, 이 데이터를 쓰는 코드는 이름과 코드로 찾는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductReferenceDataService {

    private static final Map<String, String> COLORS = colors();

    private final AttributeRepository attributeRepository;
    private final AttributeValueRepository attributeValueRepository;
    private final ProductTypeRepository productTypeRepository;
    private final MeasurementItemRepository measurementItemRepository;
    private final CategoryRepository categoryRepository;

    // 상품 유형이 하나라도 있으면 이미 넣었거나 관리자가 만든 것이므로 통째로 건너뛴다
    @Transactional
    public void load() {
        if (productTypeRepository.count() > 0) {
            log.info("상품 유형이 이미 있어 상품 기준 데이터를 넣지 않는다");
            return;
        }
        Attribute color = attributeRepository.save(Attribute.create("색상", "COLOR", AttributeInputType.PALETTE));
        Attribute size = attributeRepository.save(Attribute.create("사이즈", "SIZE", AttributeInputType.LIST));
        Attribute material = attributeRepository.save(
            Attribute.create("주요소재", "MAIN_MATERIAL", AttributeInputType.LIST));
        Attribute composition = attributeRepository.save(
            Attribute.create("혼용률", "FABRIC_COMPOSITION", AttributeInputType.TEXT));
        saveColors(color);
        saveValues(size, List.of("90", "100", "110", "120", "130", "140", "150", "FREE"));
        saveValues(material, List.of("면", "폴리에스테르", "아크릴", "기타"));

        ProductType clothing = productTypeRepository.save(
            ProductType.define("의류", clothingLinks(color, size, material, composition)));
        Map<String, MeasurementItem> items = saveMeasurementItems();
        saveCategories(clothing, items);
        log.info("상품 기준 데이터를 넣었다");
    }

    // 색상 값은 이름과 색 코드를 함께 넣는다
    private void saveColors(Attribute color) {
        for (Map.Entry<String, String> entry : COLORS.entrySet()) {
            attributeValueRepository.save(AttributeValue.create(color, entry.getKey(), entry.getValue()));
        }
    }

    private void saveValues(Attribute attribute, List<String> names) {
        for (String name : names) {
            attributeValueRepository.save(AttributeValue.create(attribute, name, null));
        }
    }

    // 연결 순서가 등록 화면의 표시 순서가 된다. 모든 연결이 모든 값을 허용하고 가격을 바꾸지 않는다
    private List<ProductType.LinkInfo> clothingLinks(
        Attribute color, Attribute size, Attribute material, Attribute composition) {
        return List.of(
            link(color, AttributeRole.OPTION, true, false),
            link(size, AttributeRole.OPTION, true, true),
            link(material, AttributeRole.INFO, true, false),
            link(composition, AttributeRole.INFO, false, false));
    }

    private ProductType.LinkInfo link(Attribute attribute, AttributeRole role, boolean required, boolean sizeRole) {
        return new ProductType.LinkInfo(attribute, role, required, true, sizeRole, false, List.of());
    }

    // 실측 항목을 넣고 코드로 찾을 수 있게 넣은 순서대로 돌려준다
    private Map<String, MeasurementItem> saveMeasurementItems() {
        Map<String, MeasurementItem> items = new LinkedHashMap<>();
        saveMeasurementItem(items, "총장", "TOTAL_LENGTH");
        saveMeasurementItem(items, "어깨너비", "SHOULDER_WIDTH");
        saveMeasurementItem(items, "가슴단면", "CHEST_WIDTH");
        saveMeasurementItem(items, "소매길이", "SLEEVE_LENGTH");
        saveMeasurementItem(items, "허리단면", "WAIST_WIDTH");
        saveMeasurementItem(items, "엉덩이단면", "HIP_WIDTH");
        saveMeasurementItem(items, "허벅지단면", "THIGH_WIDTH");
        saveMeasurementItem(items, "밑위", "RISE");
        saveMeasurementItem(items, "머리둘레", "HEAD_CIRCUMFERENCE");
        saveMeasurementItem(items, "발 길이", "FOOT_LENGTH");
        return items;
    }

    private void saveMeasurementItem(Map<String, MeasurementItem> items, String name, String code) {
        items.put(code, measurementItemRepository.save(MeasurementItem.create(name, code, "cm")));
    }

    // 중간 카테고리를 먼저 넣고 그 아래 말단 카테고리를 넣는다
    private void saveCategories(ProductType clothing, Map<String, MeasurementItem> items) {
        Category clothes = categoryRepository.save(Category.createMiddle("의류", null));
        saveLeaf(clothes, clothing, items, "상의", "TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH", "SLEEVE_LENGTH");
        saveLeaf(clothes, clothing, items, "하의",
            "TOTAL_LENGTH", "WAIST_WIDTH", "HIP_WIDTH", "THIGH_WIDTH", "RISE");
        saveLeaf(clothes, clothing, items, "아우터",
            "TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH", "SLEEVE_LENGTH");
        saveLeaf(clothes, clothing, items, "원피스", "TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH");
        Category goods = categoryRepository.save(Category.createMiddle("잡화", null));
        saveLeaf(goods, clothing, items, "모자", "HEAD_CIRCUMFERENCE");
        saveLeaf(goods, clothing, items, "양말", "FOOT_LENGTH");
    }

    // 필수 실측 항목은 코드를 적은 순서대로 둔다
    private void saveLeaf(Category parent, ProductType type, Map<String, MeasurementItem> items, String name,
        String... measurementCodes) {
        List<MeasurementItem> required = new ArrayList<>();
        for (String code : measurementCodes) {
            required.add(items.get(code));
        }
        categoryRepository.save(Category.createLeaf(new Category.LeafInfo(name, parent, type, required)));
    }

    private static Map<String, String> colors() {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put("네이비", "#2f3e5c");
        colors.put("그레이", "#9e9e9e");
        colors.put("오트밀", "#e3d3bc");
        colors.put("코랄 핑크", "#e8a598");
        colors.put("화이트", "#ffffff");
        colors.put("블루", "#3f6fb5");
        colors.put("베이지", "#d8c3a5");
        colors.put("크림", "#f3ead3");
        colors.put("블랙", "#222222");
        colors.put("레드", "#d64545");
        colors.put("옐로", "#f2c94c");
        colors.put("그린", "#6aa36f");
        return colors;
    }
}
