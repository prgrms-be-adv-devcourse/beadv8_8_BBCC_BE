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
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 판매자가 등록한 상품의 고칠 수 있는 칸을 받은 값으로 바꿔 넣는 동작을 확인한다.
 * 상품 A는 판매자 2의 상의이고 주요소재는 면이며, 마지막으로 읽은 version은 3이다.
 */
class ProductUpdateTest {

    private static final long VERSION = 3L;

    private final ProductFixture.Clothing clothing = ProductFixture.clothing();
    private final Product.RegisterInfo registered = productA(clothing);
    private final Category category = registered.category();
    private final Product product = registeredProduct();

    @Test
    @DisplayName("기본 정보를 받은 값으로 바꾸고 바뀌었다고 돌려준다. 썸네일은 null로 보내면 지운다")
    void updateBasicInfo() {
        boolean changed = product.update(new Product.UpdateInfo(SELLER_ID, VERSION, category, "상품 A 새 이름",
            "새 브랜드", "새 설명", null, List.of("https://…/a-2.jpg", "https://…/a-3.jpg"),
            List.of(cottonMaterial(clothing))));

        assertThat(changed).isTrue();
        assertThat(product.getName()).isEqualTo("상품 A 새 이름");
        assertThat(product.getBrand()).isEqualTo("새 브랜드");
        assertThat(product.getDescription()).isEqualTo("새 설명");
        assertThat(product.getThumbnailUrl()).isNull();
        assertThat(product.getImageUrls()).containsExactly("https://…/a-2.jpg", "https://…/a-3.jpg");
    }

    @Test
    @DisplayName("정보 항목 값을 받은 값으로 통째로 바꾼다. 받지 않은 이전 값은 남지 않는다")
    void replaceInfoValues() {
        boolean changed = product.update(sameBasicsWith(List.of(
            new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.linen()), null),
            new Product.InfoValueInfo(clothing.feature(), List.of(clothing.brushed(), clothing.stretch()), null),
            new Product.InfoValueInfo(clothing.fabricComposition(), List.of(), "면 95%, 폴리우레탄 5%"))));

        assertThat(changed).isTrue();
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

        boolean changed = product.update(sameBasicsWith(List.of(cottonMaterial(clothing))));

        assertThat(changed).isFalse();
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

        assertThat(product.update(retried)).isFalse();
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
