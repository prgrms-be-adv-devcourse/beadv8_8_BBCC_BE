package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    // 저장하고 바로 DB에 반영해, 늘어난 version을 같은 트랜잭션에서 읽을 수 있게 한다
    Product saveAndFlush(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();

    long count();

    boolean existsByCategory(Category category);

    boolean existsByCategoryProductType(ProductType productType);

    List<ProductOption> findOptionsWithProduct(Collection<Long> optionIds);

    // 재고 차감 트랜잭션 안에서만 불러야 한다. 바깥 트랜잭션이 잡은 잠금을 같은 스레드의 안쪽 새 트랜잭션이 기다리면
    // PostgreSQL이 교착으로 감지하지 못하고 멈춘다
    List<Product> lockAllHavingOptions(Collection<Long> optionIds);

    List<String> findSkuCodesInUse(Long sellerId, Collection<String> skuCodes);

    List<String> findSkuCodesInUseByOtherProducts(Long sellerId, Long productId, Collection<String> skuCodes);

    // 상품 행의 칸이 바뀌지 않아도 부르는 즉시 version을 1 올린다. 다른 요청이 먼저 고쳐 version이 달라졌으면 그 자리에서 충돌한다.
    // SKU 코드 · 판매가 · 실측처럼 자식 행만 바뀐 수정에서 version을 확실히 올리려고 쓴다
    void incrementVersion(Product product);

    default Product getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
