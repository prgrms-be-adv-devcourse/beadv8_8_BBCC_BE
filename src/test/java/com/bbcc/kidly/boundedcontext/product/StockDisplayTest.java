package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.addSizeSpecs;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.StockDisplay;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.StockStatus;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StockDisplayTest {

    private final ClothingOptions clothing = clothingOptions();
    private final Product product = productA(clothing);
    private final Category top = CategoryFixture.top(CategoryFixture.clothing(), clothing.type());

    @ParameterizedTest(name = "판매 가능 수량 {0} → {1}, 수량 {2}")
    @CsvSource({
        "8, AVAILABLE, ",
        "6, AVAILABLE, ",
        "5, LOW, 5",
        "1, LOW, 1",
        "0, SOLD_OUT, "})
    @DisplayName("SALE-09 판매 가능 수량이 기준 수 5 이하일 때만 수량을 보여 주고, 0이면 품절이다")
    void showQuantityOnlyAtOrBelowThreshold(int availableQty, StockStatus expectedStatus, Integer expectedQuantity) {
        ProductOption option = optionOnSale();

        StockDisplay display = StockDisplay.of(option, availableQty);

        assertThat(display.status()).isEqualTo(expectedStatus);
        assertThat(display.quantity()).isEqualTo(expectedQuantity);
    }

    // 판매를 시작한 상품 A의 네이비 110 옵션을 돌려준다
    private ProductOption optionOnSale() {
        product.addOptions(addOptions(List.of(option(clothing.navy(), clothing.size110()))), clothing.type());
        product.addSizeSpecs(addSizeSpecs(List.of(spec(clothing.size110(), 105, 115, top110Measurements()))),
            top, clothing.type());
        product.startSale(SELLER_ID, top, clothing.type());
        return product.getOptions().get(0);
    }
}
