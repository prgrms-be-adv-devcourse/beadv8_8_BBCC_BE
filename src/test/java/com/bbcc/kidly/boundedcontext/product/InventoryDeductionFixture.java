package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.addOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingOptions;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.option;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.productA;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.addSizeSpecs;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.spec;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top110Measurements;
import static com.bbcc.kidly.boundedcontext.product.SizeSpecFixture.top120Measurements;

import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

final class InventoryDeductionFixture {

    static final long ORDER_ID = 100L;
    static final long NAVY_110_ID = 7L;
    static final long NAVY_120_ID = 3L;

    private InventoryDeductionFixture() {
    }

    /**
     * 상품 A의 네이비 · 110과 네이비 · 120 옵션이다. 차감 서비스가 DB에서 읽은 옵션처럼 ID가 있다.
     * 옵션 ID는 등록 순서와 반대(110이 7, 120이 3)라서 옵션 ID 오름차순을 확인할 수 있다.
     */
    record Options(ProductOption navy110, ProductOption navy120) {
    }

    // 판매를 시작한 상품의 옵션이다. 두 옵션 모두 살 수 있다
    static Options onSaleOptions() {
        return options(true);
    }

    // 판매를 시작하기 전(판매대기) 상품의 옵션이다. 두 옵션 모두 살 수 없다
    static Options beforeSaleOptions() {
        return options(false);
    }

    static InventoryDeductionRequest.Item item(long orderItemId, ProductOption option, int qty) {
        return new InventoryDeductionRequest.Item(orderItemId, option, qty);
    }

    // 상품 A에 네이비 110 · 120 옵션과 두 사이즈의 필수 실측 · 권장 키를 넣고, startSale이면 판매를 시작한다
    private static Options options(boolean startSale) {
        ClothingOptions clothing = clothingOptions();
        Product product = productA(clothing);
        Category top = CategoryFixture.top(CategoryFixture.clothing(), clothing.type());
        product.addOptions(addOptions(List.of(
            option(clothing.navy(), clothing.size110()),
            option(clothing.navy(), clothing.size120()))), clothing.type());
        product.addSizeSpecs(addSizeSpecs(List.of(
            spec(clothing.size110(), 105, 115, top110Measurements()),
            spec(clothing.size120(), 115, 125, top120Measurements()))), top, clothing.type());
        if (startSale) {
            product.startSale(SELLER_ID, top, clothing.type());
        }
        ProductOption navy110 = product.getOptions().get(0);
        ProductOption navy120 = product.getOptions().get(1);
        ReflectionTestUtils.setField(navy110, "id", NAVY_110_ID);
        ReflectionTestUtils.setField(navy120, "id", NAVY_120_ID);
        return new Options(navy110, navy120);
    }
}
