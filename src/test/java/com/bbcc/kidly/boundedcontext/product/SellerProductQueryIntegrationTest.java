package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.CLOTHING_PRICE;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.OTHER_SELLER_ID;
import static com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.clothingPrice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bbcc.kidly.boundedcontext.product.ClothingTopSaver.SavedClothingTop;
import com.bbcc.kidly.boundedcontext.product.ProductOptionFixture.ClothingOptions;
import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductQuery;
import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductSummary;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValue;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 판매자 내 상품 목록 조회를 실제 PostgreSQL로 확인한다. 테스트에 @Transactional을 붙이지 않는다.
 */
@IntegrationTest
class SellerProductQueryIntegrationTest {

    @Autowired
    private SellerProductQuery sellerProductQuery;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ClothingTopSaver clothingTopSaver;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("판매자 본인의 상품만 판매 상태와 상관없이 등록 최신순으로 돌려준다")
    void findBySellerReturnsOwnProductsNewestFirst() {
        SavedClothingTop saved = clothingTopSaver.save();
        Product pending = saveProduct(saved, SELLER_ID, "판매대기 상품", 10);
        Product onSale = saveProduct(saved, SELLER_ID, "판매중 상품", 10);
        saveProduct(saved, OTHER_SELLER_ID, "다른 판매자 상품", 10);
        Product stopped = saveProduct(saved, SELLER_ID, "판매중지 상품", 10);
        changeStatus(onSale, "ON_SALE");
        changeStatus(stopped, "STOPPED");

        Page<SellerProductSummary> page = sellerProductQuery.findBySeller(SELLER_ID, PageRequest.of(0, 10));

        assertThat(page.getContent())
            .extracting(SellerProductSummary::productId, SellerProductSummary::name, SellerProductSummary::status)
            .containsExactly(
                tuple(stopped.getId(), "판매중지 상품", ProductStatus.STOPPED),
                tuple(onSale.getId(), "판매중 상품", ProductStatus.ON_SALE),
                tuple(pending.getId(), "판매대기 상품", ProductStatus.SALE_PENDING));
    }

    @Test
    @DisplayName("SALE-06 보관 옵션은 옵션 수 · 재고 합계 · 최저가에서 빼고, 판매중지 옵션은 넣는다")
    void findBySellerExcludesArchivedOptionsFromSummary() {
        SavedClothingTop saved = clothingTopSaver.save();
        Product product = saveProduct(saved, SELLER_ID, "상품 A", 10, 5, 7);
        changeOption(product.getOptions().get(1).getId(), "STOPPED", 20_000L);
        changeOption(product.getOptions().get(2).getId(), "ARCHIVED", 15_000L);

        Page<SellerProductSummary> page = sellerProductQuery.findBySeller(SELLER_ID, PageRequest.of(0, 10));

        assertThat(page.getContent())
            .extracting(SellerProductSummary::optionCount, SellerProductSummary::totalAvailableQuantity,
                SellerProductSummary::minPrice)
            .containsExactly(tuple(2L, 15L, 20_000L));
    }

    @Test
    @DisplayName("SALE-06 옵션이 모두 보관이면 옵션 수와 재고 합계는 0, 최저가는 null이다")
    void findBySellerReturnsEmptySummaryWhenAllOptionsArchived() {
        SavedClothingTop saved = clothingTopSaver.save();
        Product product = saveProduct(saved, SELLER_ID, "상품 A", 10, 5);
        changeOption(product.getOptions().get(0).getId(), "ARCHIVED", CLOTHING_PRICE);
        changeOption(product.getOptions().get(1).getId(), "ARCHIVED", CLOTHING_PRICE);

        Page<SellerProductSummary> page = sellerProductQuery.findBySeller(SELLER_ID, PageRequest.of(0, 10));

        assertThat(page.getContent())
            .extracting(SellerProductSummary::optionCount, SellerProductSummary::totalAvailableQuantity,
                SellerProductSummary::minPrice)
            .containsExactly(tuple(0L, 0L, null));
    }

    @Test
    @DisplayName("페이지 크기만큼 돌려주고, 전체 건수와 다음 페이지 여부는 판매자 본인의 상품으로 센다")
    void findBySellerPagesOwnProducts() {
        SavedClothingTop saved = clothingTopSaver.save();
        saveProduct(saved, SELLER_ID, "첫째 상품", 10);
        Product second = saveProduct(saved, SELLER_ID, "둘째 상품", 10);
        Product third = saveProduct(saved, SELLER_ID, "셋째 상품", 10);
        saveProduct(saved, OTHER_SELLER_ID, "다른 판매자 상품", 10);

        Page<SellerProductSummary> page = sellerProductQuery.findBySeller(SELLER_ID, PageRequest.of(0, 2));

        assertThat(page.getContent())
            .extracting(SellerProductSummary::productId)
            .containsExactly(third.getId(), second.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
    }

    // 판매자의 판매대기 상품 하나를 저장하고 옵션마다 재고를 연다.
    // 옵션은 네이비 110 · 네이비 120 · 오트밀 110 · 오트밀 120 순서로 재고 수만큼 만들고, 판매가는 모두 25,000원이다
    private Product saveProduct(SavedClothingTop saved, long sellerId, String name, int... stocks) {
        ClothingOptions clothing = saved.clothing();
        Product product = Product.register(ProductFixture.productAWithRequired(sellerId, saved.top(), name,
            List.of("https://cdn.kidly.test/a-1.jpg"),
            List.of(new Product.InfoValueInfo(clothing.mainMaterial(), List.of(clothing.cotton()), null))));
        List<List<AttributeValue>> valueLists = List.of(
            List.of(clothing.navy(), clothing.size110()),
            List.of(clothing.navy(), clothing.size120()),
            List.of(clothing.oatmeal(), clothing.size110()),
            List.of(clothing.oatmeal(), clothing.size120()));
        List<Product.OptionInfo> options = new ArrayList<>();
        for (int i = 0; i < stocks.length; i++) {
            options.add(new Product.OptionInfo(valueLists.get(i), null));
        }
        product.addOptions(
            new Product.AddOptionsInfo(sellerId, options, clothingPrice(CLOTHING_PRICE)), clothing.type());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            productRepository.save(product);
            for (int i = 0; i < stocks.length; i++) {
                inventoryRepository.saveOpening(
                    InventoryOpening.open(product.getOptions().get(i), stocks[i], sellerId));
            }
        });
        return product;
    }

    // 판매중지로 바꾸는 업무 메서드가 아직 없고, 판매 시작은 실측까지 넣어야 해서 표를 직접 고친다
    private void changeStatus(Product product, String status) {
        jdbcTemplate.update("update product.product set status = ? where id = ?", status, product.getId());
    }

    // 옵션의 판매 상태와 판매가를 바꾸는 업무 메서드가 아직 없어 표를 직접 고친다.
    // 의류는 상품 하나에 판매가가 하나라서, 옵션마다 다른 판매가도 표를 고쳐서 만든다
    private void changeOption(Long optionId, String saleStatus, long price) {
        jdbcTemplate.update("update product.product_option set sale_status = ?, price = ? where id = ?",
            saleStatus, price, optionId);
    }
}
