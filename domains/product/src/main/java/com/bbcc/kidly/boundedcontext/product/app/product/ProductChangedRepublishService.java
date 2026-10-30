package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductChangedPublisher;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미 판매를 시작한 상품의 상품 정보 변경됨을 관리자가 다시 보낼 수 있게 한다.
 * 판매를 시작하기 전 상품은 상품 정보 변경됨을 보내지 않는다는 규칙(SALE-08)대로 판매대기 상품은 건너뛴다.
 */
@Service
@RequiredArgsConstructor
public class ProductChangedRepublishService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductChangedPublisher productChangedPublisher;

    // 판매중 · 판매중지 상품마다 상품 정보 변경됨을 한 번씩 발행하고, 보낸 상품 수를 돌려준다
    // 이벤트가 아웃박스에 저장되므로, 전체 상품을 읽는 이 트랜잭션이 커밋될 때만 Kafka로 나간다
    @Transactional
    public int republishStartedSaleProducts() {
        int publishedCount = 0;
        for (Product product : productRepository.findAll()) {
            if (!product.hasStartedSale()) {
                continue;
            }
            Category category = categoryRepository.getById(product.getCategoryId());
            productChangedPublisher.publish(product, category.getProductType());
            publishedCount++;
        }
        return publishedCount;
    }
}
