package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.cottonMaterial;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductInfoValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 판매자가 등록한 상품의 고칠 수 있는 칸을 받은 값으로 바꿔 넣는 동작을 확인한다.
 * 상품 A는 판매자 2의 상의이고 주요소재는 면이며, 마지막으로 읽은 version은 3이다.
 */
class ProductUpdateTest {

    private static final long VERSION = 3L;
    private static final String BRAND = "키들리";
    private static final String DESCRIPTION = "상품 설명";
    private static final String THUMBNAIL_URL = "https://…/a-thumbnail.jpg";
    private static final List<String> TWO_IMAGES = List.of("https://…/a-1.jpg", "https://…/a-2.jpg");
    private static final String COMPOSITION = "면 100%";

    private final ProductFixture.Clothing clothing = ProductFixture.clothing();
    private final Product.RegisterInfo registered = productA(clothing);
    private final Category category = registered.category();
    private final Product product = registeredProduct();

    @Test
    @DisplayName("기본 정보를 받은 값으로 바꾸고 바뀌었다고 돌려준다. 썸네일은 null로 보내면 지운다")
    void updateBasicInfo() {
        Product.UpdateResult result = product.update(new Product.UpdateInfo(SELLER_ID, VERSION, category, "상품 A 새 이름",
            "새 브랜드", "새 설명", null, List.of("https://…/a-2.jpg", "https://…/a-3.jpg"),
            List.of(cottonMaterial(clothing))));

        assertThat(result.changed()).isTrue();
        assertThat(product.getName()).isEqualTo("상품 A 새 이름");
        assertThat(product.getBrand()).isEqualTo("새 브랜드");
        assertThat(product.getDescription()).isEqualTo("새 설명");
        assertThat(product.getThumbnailUrl()).isNull();
        assertThat(product.getImageUrls()).containsExactly("https://…/a-2.jpg", "https://…/a-3.jpg");
    }

    @Test
    @DisplayName("정보 항목 값을 받은 값으로 통째로 바꾼다. 받지 않은 이전 값은 남지 않는다")
    void replaceInfoValues() {
        Product.UpdateResult result = product.update(sameBasicsWith(List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.linen()), null),
            new Product.InfoValueInfo(clothing.feature(), List.of(clothing.brushed(), clothing.stretch()), null),
            new Product.InfoValueInfo(clothing.fabricComposition(), List.of(), "면 95%, 폴리우레탄 5%"))));

        assertThat(result.changed()).isTrue();
        assertThat(product.getInfoValues())
            .extracting(
                ProductInfoValue::getAttribute, ProductInfoValue::getAttributeValue, ProductInfoValue::getTextValue)
            .containsExactly(
                tuple(clothing.mainMaterial(), clothing.linen(), null),
                tuple(clothing.feature(), clothing.brushed(), null),
                tuple(clothing.feature(), clothing.stretch(), null),
                tuple(clothing.fabricComposition(), null, "면 95%, 폴리우레탄 5%"));
    }

    @Test
    @DisplayName("받은 값이 지금과 같으면 아무것도 바꾸지 않고 바뀌지 않았다고 돌려준다. 정보 값 행도 새로 만들지 않는다")
    void keepWhenNothingChanged() {
        List<ProductInfoValue> infoValuesBefore = List.copyOf(product.getInfoValues());

        Product.UpdateResult result = product.update(sameBasicsWith(List.of(cottonMaterial(clothing))));

        assertThat(result.changed()).isFalse();
        assertThat(product.getInfoValues()).containsExactlyElementsOf(infoValuesBefore);
    }

    @Test
    @DisplayName("바꿀 것이 있는데 마지막으로 읽은 version이 지금과 다르면 409로 거절하고 아무것도 바꾸지 않는다")
    void rejectStaleVersion() {
        Product.UpdateInfo stale = new Product.UpdateInfo(SELLER_ID, VERSION - 1, category, "상품 A 새 이름",
            registered.brand(), registered.description(), registered.thumbnailUrl(), registered.imageUrls(),
            List.of(cottonMaterial(clothing)));

        assertThatThrownBy(() -> product.update(stale)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_VERSION_MISMATCH));
        assertThat(product.getName()).isEqualTo(registered.name());
    }

    @Test
    @DisplayName("바꿀 것이 없으면 version이 달라도 거절하지 않는다. 이미 반영된 요청을 다시 보낸 경우다")
    void acceptRetryOfAppliedUpdate() {
        Product.UpdateInfo retried = new Product.UpdateInfo(SELLER_ID, VERSION - 1, category, registered.name(),
            registered.brand(), registered.description(), registered.thumbnailUrl(), registered.imageUrls(),
            List.of(cottonMaterial(clothing)));

        assertThat(product.update(retried).changed()).isFalse();
    }

    @ParameterizedTest(name = "상품명 {0}")
    @ValueSource(strings = {"상품 A 새 이름", "상품 A"})
    @DisplayName("OPT-08 상품을 등록한 판매자가 아니면 바꾸는 값이든 같은 값이든 403으로 거절하고 아무것도 바꾸지 않는다")
    void rejectUpdateFromOtherSeller(String name) {
        Product.UpdateInfo fromOtherSeller = new Product.UpdateInfo(SELLER_ID + 1, VERSION, category, name,
            registered.brand(), registered.description(), registered.thumbnailUrl(), registered.imageUrls(),
            List.of(cottonMaterial(clothing)));

        assertThatThrownBy(() -> product.update(fromOtherSeller)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.NOT_PRODUCT_OWNER));
        assertThat(product.getName()).isEqualTo(registered.name());
    }

    @Test
    @DisplayName("INFO-02 필수 정보 항목(주요소재)을 빼고 고치면 거절하고 아무것도 바꾸지 않는다")
    void rejectWithoutRequiredInfo() {
        Product.UpdateInfo withoutMaterial = new Product.UpdateInfo(SELLER_ID, VERSION, category, "상품 A 새 이름",
            registered.brand(), registered.description(), registered.thumbnailUrl(), registered.imageUrls(),
            List.of());

        assertThatThrownBy(() -> product.update(withoutMaterial)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.REQUIRED_INFO_MISSING));
        assertThat(product.getName()).isEqualTo(registered.name());
    }

    @Test
    @DisplayName("INFO-01 다중 선택이 아닌 정보 항목(주요소재)에 값을 두 개 넣어 고치면 거절하고 아무것도 바꾸지 않는다")
    void rejectMultipleValuesOnSingleInfo() {
        Product.UpdateInfo twoMaterials = sameBasicsWith(List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton(), clothing.linen()), null)));

        assertThatThrownBy(() -> product.update(twoMaterials)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.MULTIPLE_VALUES_NOT_ALLOWED));
        assertThat(product.getInfoValues()).extracting(ProductInfoValue::getAttributeValue)
            .containsExactly(clothing.cotton());
    }

    @ParameterizedTest(name = "상품명 [{0}]")
    @ValueSource(strings = {"", "   "})
    @DisplayName("상품명이 비어 있으면 고치지 않는다")
    void rejectBlankName(String name) {
        Product.UpdateInfo blankName = new Product.UpdateInfo(SELLER_ID, VERSION, category, name,
            registered.brand(), registered.description(), registered.thumbnailUrl(), registered.imageUrls(),
            List.of(cottonMaterial(clothing)));

        assertThatThrownBy(() -> product.update(blankName)).isInstanceOf(IllegalArgumentException.class);
        assertThat(product.getName()).isEqualTo(registered.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("updatesWithNull")
    @DisplayName("수정 값 · 판매자 ID · version · 카테고리 · 상품명 · 이미지 목록 · 정보 값이 null이면 코드가 잘못 호출된 것이라 고치지 않는다")
    void rejectUpdateWithNull(Function<ProductUpdateTest, Product.UpdateInfo> updateWithNull) {
        Product.UpdateInfo info = updateWithNull.apply(this);

        assertThatThrownBy(() -> product.update(info))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("필수입니다");
        assertThat(product.getName()).isEqualTo(registered.name());
    }

    static Stream<Named<Function<ProductUpdateTest, Product.UpdateInfo>>> updatesWithNull() {
        return Stream.of(
            Named.of("수정 값", test -> null),
            Named.of("판매자 ID", test -> test.updateWith(null, VERSION, test.category, "새 이름", List.of(),
                List.of(cottonMaterial(test.clothing)))),
            Named.of("version", test -> test.updateWith(SELLER_ID, null, test.category, "새 이름", List.of(),
                List.of(cottonMaterial(test.clothing)))),
            Named.of("카테고리", test -> test.updateWith(SELLER_ID, VERSION, null, "새 이름", List.of(),
                List.of(cottonMaterial(test.clothing)))),
            Named.of("상품명", test -> test.updateWith(SELLER_ID, VERSION, test.category, null, List.of(),
                List.of(cottonMaterial(test.clothing)))),
            Named.of("이미지 목록", test -> test.updateWith(SELLER_ID, VERSION, test.category, "새 이름", null,
                List.of(cottonMaterial(test.clothing)))),
            Named.of("정보 값", test -> test.updateWith(SELLER_ID, VERSION, test.category, "새 이름", List.of(),
                null)));
    }

    // 브랜드 · 설명 · 썸네일은 상품 A의 값으로 두고 필수 값만 받아 수정 값을 만든다
    private Product.UpdateInfo updateWith(Long sellerId, Long version, Category category, String name,
        List<String> imageUrls, List<Product.InfoValueInfo> infoValues) {
        return new Product.UpdateInfo(sellerId, version, category, name, registered.brand(),
            registered.description(), registered.thumbnailUrl(), imageUrls, infoValues);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleFieldChanges")
    @DisplayName("칸 하나만 바꿔도 바뀌었다고 돌려주고 반영한다. 반영한 뒤 같은 요청을 다시 보내면 바뀌지 않았다고 돌려준다")
    void detectSingleFieldChange(Function<ProductUpdateTest, Product.UpdateInfo> changeOneField) {
        Product detailed = detailedProduct();
        Product.UpdateInfo info = changeOneField.apply(this);

        assertThat(detailed.update(info).changed()).isTrue();
        assertThat(detailed.update(info).changed()).isFalse();
    }

    static Stream<Named<Function<ProductUpdateTest, Product.UpdateInfo>>> singleFieldChanges() {
        return Stream.of(
            Named.of("브랜드", test -> test.detailedUpdate(
                "새 브랜드", DESCRIPTION, THUMBNAIL_URL, TWO_IMAGES, test.clothing.cotton(), COMPOSITION)),
            Named.of("설명", test -> test.detailedUpdate(
                BRAND, "새 설명", THUMBNAIL_URL, TWO_IMAGES, test.clothing.cotton(), COMPOSITION)),
            Named.of("썸네일 지우기", test -> test.detailedUpdate(
                BRAND, DESCRIPTION, null, TWO_IMAGES, test.clothing.cotton(), COMPOSITION)),
            Named.of("이미지 순서만", test -> test.detailedUpdate(
                BRAND, DESCRIPTION, THUMBNAIL_URL, TWO_IMAGES.reversed(), test.clothing.cotton(), COMPOSITION)),
            Named.of("주요소재 면 → 린넨", test -> test.detailedUpdate(
                BRAND, DESCRIPTION, THUMBNAIL_URL, TWO_IMAGES, test.clothing.linen(), COMPOSITION)),
            Named.of("혼용률 글만", test -> test.detailedUpdate(
                BRAND, DESCRIPTION, THUMBNAIL_URL, TWO_IMAGES, test.clothing.cotton(), "면 95%, 폴리우레탄 5%")));
    }

    // 브랜드 · 설명 · 썸네일 · 이미지 2장 · 주요소재 · 혼용률을 모두 채운 상품이다. 칸 하나만 바꾼 수정을 확인할 때 쓴다
    private Product detailedProduct() {
        Product detailed = Product.register(new Product.RegisterInfo(SELLER_ID, category, "상품 A", BRAND,
            DESCRIPTION, THUMBNAIL_URL, TWO_IMAGES, detailedInfoValues(clothing.cotton(), COMPOSITION)));
        ReflectionTestUtils.setField(detailed, "version", VERSION);
        return detailed;
    }

    private Product.UpdateInfo detailedUpdate(String brand, String description, String thumbnailUrl,
        List<String> imageUrls, AttributeValue material, String composition) {
        return new Product.UpdateInfo(SELLER_ID, VERSION, category, "상품 A", brand, description, thumbnailUrl,
            imageUrls, detailedInfoValues(material, composition));
    }

    private List<Product.InfoValueInfo> detailedInfoValues(AttributeValue material, String composition) {
        return List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(material), null),
            new Product.InfoValueInfo(clothing.fabricComposition(), List.of(), composition));
    }

    // 등록한 상품 A와 기본 정보는 같고 정보 값만 바꾼 수정 값이다
    private Product.UpdateInfo sameBasicsWith(List<Product.InfoValueInfo> infoValues) {
        return new Product.UpdateInfo(SELLER_ID, VERSION, category, registered.name(), registered.brand(),
            registered.description(), registered.thumbnailUrl(), registered.imageUrls(), infoValues);
    }

    // 저장하지 않은 상품은 version이 없으므로 마지막으로 읽은 값을 넣어 둔다
    private Product registeredProduct() {
        Product registeredProduct = Product.register(registered);
        ReflectionTestUtils.setField(registeredProduct, "version", VERSION);
        return registeredProduct;
    }
}
