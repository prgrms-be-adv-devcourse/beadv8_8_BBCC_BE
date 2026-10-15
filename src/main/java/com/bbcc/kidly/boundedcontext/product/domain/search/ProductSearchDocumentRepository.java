package com.bbcc.kidly.boundedcontext.product.domain.search;

// 검색용 표에는 save · findById를 두지 않는다. ID를 직접 넣는 엔티티는 JpaRepository.save()가 merge로 동작해 이벤트 시각 비교를 건너뛰기 때문이다
public interface ProductSearchDocumentRepository {

    // 그 상품의 행이 없으면 만들고, 있으면 저장된 이벤트 발생 시각보다 늦은 경우에만 행 전체를 덮어쓴다.
    // 반영했으면 true, 같거나 이른 이벤트라 아무것도 바꾸지 않았으면 false를 돌려준다
    boolean saveIfNewer(ProductSearchDocument document);
}
