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
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeduction;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionRequest;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryDeductionStatus;
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

    record Options(ProductOption navy110, ProductOption navy120) {
    }

    static Options onSaleOptions() {
        return options(true);
    }

    static Options beforeSaleOptions() {
        return options(false);
    }

    static InventoryDeductionRequest.Item item(long orderItemId, ProductOption option, int qty) {
        return new InventoryDeductionRequest.Item(orderItemId, option, qty);
    }

    static List<InventoryDeduction> asLoadedWithStatus(
        List<InventoryDeduction> records, InventoryDeductionStatus status) {
        for (InventoryDeduction record : records) {
            ReflectionTestUtils.setField(record, "status", status);
        }
        return records;
    }

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
            product.startSale(SELLER_ID, top, clothing.type(), ProductFixture.NOW);
        }
        ProductOption navy110 = product.getOptions().get(0);
        ProductOption navy120 = product.getOptions().get(1);
        ReflectionTestUtils.setField(navy110, "id", NAVY_110_ID);
        ReflectionTestUtils.setField(navy120, "id", NAVY_120_ID);
        return new Options(navy110, navy120);
    }
}
