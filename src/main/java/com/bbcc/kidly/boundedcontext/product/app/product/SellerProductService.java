package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductQuery;
import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductSummary;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.SellerProductDetailResult;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 본인의 상품 조회 유스케이스.
 */
@Service
@RequiredArgsConstructor
public class SellerProductService {

    private final SellerProductQuery sellerProductQuery;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    // 정렬은 등록 최신순으로 고정이라 pageable의 sort는 무시된다
    @Transactional(readOnly = true)
    public Page<SellerProductSummary> findMyProducts(Long sellerId, Pageable pageable) {
        return sellerProductQuery.findBySeller(sellerId, pageable);
    }

    // 내 상품 상세를 돌려준다. 엔티티는 트랜잭션 안에서 읽어 결과로 모두 바꾼다
    @Transactional(readOnly = true)
    public SellerProductDetailResult findMyProductDetail(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.validateViewer(sellerId);
        Map<Long, Integer> availableQtyByOptionId = availableQtyByOptionId(product.getOptions());
        List<SellerProductDetailResult.Option> options = new ArrayList<>();
        for (ProductOption option : product.getOptions()) {
            // 보관 옵션은 판매자 화면에도 보이지 않는다 (SALE-06)
            if (option.isArchived()) {
                continue;
            }
            // 재고 행이 없는 옵션은 판매 가능 수량 0으로 본다
            options.add(new SellerProductDetailResult.Option(
                option.getId(),
                option.getName(),
                option.getPrice(),
                availableQtyByOptionId.getOrDefault(option.getId(), 0),
                option.getSaleStatus().name(),
                option.getSkuCode()));
        }
        return new SellerProductDetailResult(
            product.getId(), product.getName(), product.getStatus().name(), product.getVersion(),
            product.getBrand(), product.getCategoryId(), product.getDescription(), product.getThumbnailUrl(),
            new ArrayList<>(product.getImageUrls()), options);
    }

    // 옵션들의 재고를 한 번에 읽어 옵션 ID별 판매 가능 수량으로 묶는다
    private Map<Long, Integer> availableQtyByOptionId(List<ProductOption> options) {
        List<Long> optionIds = new ArrayList<>();
        for (ProductOption option : options) {
            optionIds.add(option.getId());
        }
        return inventoryRepository.findAvailableQtyByOptionId(optionIds);
    }
}
