package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.cottonMaterial;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
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

    // 저장하지 않은 상품은 version이 없으므로 마지막으로 읽은 값을 넣어 둔다
    private Product registeredProduct() {
        Product registeredProduct = Product.register(registered);
        ReflectionTestUtils.setField(registeredProduct, "version", VERSION);
        return registeredProduct;
    }
}
