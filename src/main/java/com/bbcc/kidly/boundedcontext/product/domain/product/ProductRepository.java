package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    // 상품을 저장한다. 옵션 · 실측 · 정보 값은 상품과 함께 저장된다
    Product save(Product product);

    // 저장하고 바로 DB에 반영해, 늘어난 version을 같은 트랜잭션에서 읽을 수 있게 한다
    Product saveAndFlush(Product product);

    // 상품을 ID로 찾는다. 없으면 빈 값을 돌려준다
    Optional<Product> findById(Long id);

    // 저장된 상품의 수를 돌려준다
    long count();

    // 그 카테고리에 등록된 상품이 하나라도 있는지 본다. 카테고리 설정을 바꿀 때 기존 상품을 깨는지 판단하는 데 쓴다
    boolean existsByCategory(Category category);

    // 그 유형을 쓰는 카테고리에 등록된 상품이 하나라도 있는지 본다. 유형 설정을 바꿀 때 기존 상품을 깨는지 판단하는 데 쓴다
    boolean existsByCategoryProductType(ProductType productType);

    // 재고를 차감할 옵션을 ID 목록으로 상품과 함께 찾는다. 없는 ID는 결과에서 빠진다.
    // 호출한 쪽에 바깥 트랜잭션이 없으면 조회 직후 옵션이 준영속이 되므로, 판매 가능 여부를 판단할 수 있게 상품을 함께 읽어 둔다.
    // 옵션마다 상품을 따로 조회하는 쿼리가 나가는 것도 막는다
    List<ProductOption> findOptionsWithProduct(Collection<Long> optionIds);

    // 옵션 ID 목록에 든 옵션이 속한 상품 행을 상품 ID 오름차순으로 비관적 쓰기 잠금(SELECT ... FOR UPDATE)으로 잠가 읽는다. 같은 상품은 한 번만 나온다
    // 잠금은 호출한 트랜잭션이 끝날 때 풀린다. 여러 상품을 잠글 때 순서를 맞춰 교착 상태를 막는다
    // 재고 차감 트랜잭션 안에서만 불러야 한다. 바깥 리스너 트랜잭션에서 잠그면, 같은 스레드에서 중첩 실행되는 다른 리스너가 그 행을 기다리다 멈춘다
    List<Product> lockAllHavingOptions(Collection<Long> optionIds);

    // 판매자의 옵션이 이미 쓰는 SKU 코드 중 skuCodes에 든 것을 돌려준다.
    // 옵션의 판매 상태(판매중 · 판매중지 · 보관)와 상관없이 찾는다. skuCodes가 비면 빈 목록을 돌려준다
    List<String> findSkuCodesInUse(Long sellerId, Collection<String> skuCodes);

    // 판매자의 다른 상품 옵션이 이미 쓰는 SKU 코드 중 skuCodes에 든 것을 돌려준다. productId 상품 자신의 옵션은 빼고 찾는다.
    // 상품을 고칠 때 그 상품이 이미 쓰는 SKU 코드를 다시 보내도 겹친다고 보지 않게 하기 위해서다
    List<String> findSkuCodesInUseByOtherProducts(Long sellerId, Long productId, Collection<String> skuCodes);

    // 상품 행의 칸이 바뀌지 않아도 부르는 즉시 version을 1 올린다. 다른 요청이 먼저 고쳐 version이 달라졌으면 그 자리에서 충돌한다.
    // SKU 코드 · 판매가 · 실측처럼 자식 행만 바뀐 수정에서 version을 확실히 올리려고 쓴다
    void incrementVersion(Product product);

    // 상품을 ID로 찾는다. 없으면 404 오류(PRODUCT_NOT_FOUND)를 던진다
    default Product getById(Long id) {
        return findById(id).orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
