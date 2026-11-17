package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.CLOTHING_PRICE;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.OTHER_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingPrice;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ProductSkuCodeQueryIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("OPT-07 같은 판매자의 옵션이 쓰는 SKU 코드를 판매 상태와 상관없이 찾고, 다른 판매자의 코드와 묻지 않은 코드는 뺀다")
    void findSkuCodesInUse() {
        SavedClothingTop saved = clothingTopSaver.save();
        saveProduct(saved, SELLER_ID, "A-NV-110", "A-NV-120", "A-OM-110", "A-OM-120");
        saveProduct(saved, OTHER_SELLER_ID, "A-NV-130");
        changeSaleStatus("A-NV-120", "STOPPED");
        changeSaleStatus("A-OM-110", "ARCHIVED");

        List<String> found = findSkuCodesInUse(SELLER_ID,
            List.of("A-NV-110", "A-NV-120", "A-OM-110", "A-NV-130", "A-NV-999"));

        assertThat(found).containsExactlyInAnyOrder("A-NV-110", "A-NV-120", "A-OM-110");
        assertThat(findSkuCodesInUse(SELLER_ID, List.of())).isEmpty();
    }

    private void saveProduct(SavedClothingTop saved, long sellerId, String... skuCodes) {
        ClothingOptions clothing = saved.clothing();
        Product product = Product.register(ProductFixture.productAWithRequired(sellerId, saved.top(), "상품 A",
            List.of("https://cdn.kidly.test/a-1.jpg"),
            List.of(new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null))));
        List<List<AttributeValue>> valueLists = List.of(
            List.of(clothing.navy(), clothing.size110()),
            List.of(clothing.navy(), clothing.size120()),
            List.of(clothing.oatmeal(), clothing.size110()),
            List.of(clothing.oatmeal(), clothing.size120()));
        List<Product.OptionInfo> options = new ArrayList<>();
        for (int i = 0; i < skuCodes.length; i++) {
            options.add(new Product.OptionInfo(valueLists.get(i), skuCodes[i]));
        }
        product.addOptions(
            new Product.AddOptionsInfo(sellerId, options, clothingPrice(CLOTHING_PRICE)), clothing.type());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> productRepository.save(product));
    }

    private void changeSaleStatus(String skuCode, String saleStatus) {
        jdbcTemplate.update(
            "update product.product_option set sale_status = ? where sku_code = ?", saleStatus, skuCode);
    }

    private List<String> findSkuCodesInUse(long sellerId, Collection<String> skuCodes) {
        return new TransactionTemplate(transactionManager).execute(
            status -> productRepository.findSkuCodesInUse(sellerId, skuCodes));
    }
}
