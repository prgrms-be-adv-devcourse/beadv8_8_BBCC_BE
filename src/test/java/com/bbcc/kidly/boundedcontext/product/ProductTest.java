package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.clothing;
import static com.bbcc.kidly.boundedcontext.product.ProductFixture.productA;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductFixture.Clothing;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    @DisplayName("SALE-01 새 상품은 판매대기 상태로 만들어지고 기본 정보를 담는다")
    void registerAsSalePending() {
        Clothing clothing = clothing();

        Product product = Product.register(productA(clothing), clothing.type());

        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE_PENDING);
        assertThat(product.getSellerId()).isEqualTo(2L);
        assertThat(product.getCategoryId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("상품 A");
        assertThat(product.getBrand()).isEqualTo("키들리");
        assertThat(product.getDescription()).isEqualTo("상품 설명");
        assertThat(product.getThumbnailUrl()).isEqualTo("https://…/a-thumbnail.jpg");
        assertThat(product.getImageUrls()).containsExactly("https://…/a-1.jpg");
    }
}
