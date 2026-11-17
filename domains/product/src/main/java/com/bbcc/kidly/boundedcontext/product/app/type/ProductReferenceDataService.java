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
import com.bbcc.kidly.shared.product.domain.CategoryVisibleGender;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public void load() {
        if (productTypeRepository.count() > 0) {
            log.info("상품 유형이 이미 있어 상품 기준 데이터를 넣지 않는다");
            return;
        }
        Attribute color = attributeRepository.save(Attribute.create("색상", "COLOR", AttributeInputType.PALETTE));
        Attribute material = attributeRepository.save(
            Attribute.create("주요소재", "MAIN_MATERIAL", AttributeInputType.LIST));
        Attribute composition = attributeRepository.save(
            Attribute.create("혼용률", "FABRIC_COMPOSITION", AttributeInputType.TEXT));
        saveColors(color);
        saveValues(material, List.of("면", "폴리에스테르", "아크릴", "기타"));

        Attribute size = saveSize("사이즈", "SIZE", List.of("90", "100", "110", "120", "130", "140", "150", "FREE"));
        Attribute hatSize = saveSize("모자 사이즈", "HAT_SIZE",
            List.of("44cm", "46cm", "48cm", "50cm", "52cm", "54cm", "FREE"));
        Attribute sockSize = saveSize("양말 사이즈", "SOCK_SIZE",
            List.of("100-120mm", "130-150mm", "160-180mm", "190-210mm", "FREE"));

        ProductType clothingType = saveType("의류", links(color, size, material, composition));
        ProductType hatType = saveType("모자", links(color, hatSize, material, composition));
        ProductType sockType = saveType("양말", links(color, sockSize, material, composition));
        Map<String, MeasurementItem> items = saveMeasurementItems();
        saveCategories(clothingType, hatType, sockType, items);
        log.info("상품 기준 데이터를 넣었다");
    }

    private void saveColors(Attribute color) {
        for (Map.Entry<String, String> entry : COLORS.entrySet()) {
            attributeValueRepository.save(AttributeValue.create(color, entry.getKey(), entry.getValue()));
        }
    }

    private Attribute saveSize(String name, String code, List<String> values) {
        Attribute size = attributeRepository.save(Attribute.create(name, code, AttributeInputType.LIST));
        saveValues(size, values);
        return size;
    }

    private ProductType saveType(String name, List<ProductType.LinkInfo> links) {
        return productTypeRepository.save(ProductType.define(name, links));
    }

    private void saveValues(Attribute attribute, List<String> names) {
        for (String name : names) {
            attributeValueRepository.save(AttributeValue.create(attribute, name, null));
        }
    }

    private List<ProductType.LinkInfo> links(
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

    private Map<String, MeasurementItem> saveMeasurementItems() {
        var items = new HashMap<String, MeasurementItem>();
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

    private void saveCategories(ProductType clothingType, ProductType hatType, ProductType sockType,
        Map<String, MeasurementItem> items) {
        Category clothingCategory = categoryRepository.save(Category.createMiddle("의류", null));
        saveLeaf(clothingCategory, clothingType, items, "상의",
            "TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH", "SLEEVE_LENGTH");
        saveLeaf(clothingCategory, clothingType, items, "바지",
            "TOTAL_LENGTH", "WAIST_WIDTH", "HIP_WIDTH", "THIGH_WIDTH", "RISE");
        saveLeaf(clothingCategory, clothingType, items, "아우터",
            "TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH", "SLEEVE_LENGTH");
        saveLeaf(clothingCategory, clothingType, items, "원피스", "TOTAL_LENGTH", "SHOULDER_WIDTH", "CHEST_WIDTH")
            .changeVisibleGender(CategoryVisibleGender.GIRL);
        saveLeaf(clothingCategory, clothingType, items, "치마", "TOTAL_LENGTH", "WAIST_WIDTH")
            .changeVisibleGender(CategoryVisibleGender.GIRL);
        Category goodsCategory = categoryRepository.save(Category.createMiddle("잡화", null));
        saveLeaf(goodsCategory, hatType, items, "모자", "HEAD_CIRCUMFERENCE");
        saveLeaf(goodsCategory, sockType, items, "양말", "FOOT_LENGTH");
    }

    private Category saveLeaf(Category parent, ProductType type, Map<String, MeasurementItem> items, String name,
        String... measurementCodes) {
        var required = new ArrayList<MeasurementItem>();
        for (String code : measurementCodes) {
            required.add(items.get(code));
        }
        return categoryRepository.save(Category.createLeaf(new Category.LeafInfo(name, parent, type, required)));
    }

    private static Map<String, String> colors() {
        var colors = new LinkedHashMap<String, String>();
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
