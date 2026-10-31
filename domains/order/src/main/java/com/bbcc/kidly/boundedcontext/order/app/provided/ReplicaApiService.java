package com.bbcc.kidly.boundedcontext.order.app.provided;

import com.bbcc.kidly.boundedcontext.order.api.ChildView;
import com.bbcc.kidly.boundedcontext.order.api.ProductOptionView;
import com.bbcc.kidly.boundedcontext.order.api.ReplicaApi;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 · 판매자 · 자녀 복사본 조회를 다른 컨텍스트에 내준다(#617, {@link ReplicaApi}). 부르는 쪽 트랜잭션에 합류한다(REQUIRED).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReplicaApiService implements ReplicaApi {

    private final ProductReplicaRepository productReplicaRepository;
    private final SellerReplicaRepository sellerReplicaRepository;
    private final ChildReplicaRepository childReplicaRepository;

    @Override
    public Optional<ProductOptionView> findProductOption(Long optionId) {
        return productReplicaRepository.findByOptionId(optionId).map(ReplicaApiService::toView);
    }

    @Override
    public List<ProductOptionView> findProductOptions(Collection<Long> optionIds) {
        return productReplicaRepository.findAllByOptionIdIn(optionIds).stream().map(ReplicaApiService::toView).toList();
    }

    @Override
    public Optional<String> findSellerName(Long sellerId) {
        return sellerReplicaRepository.findBySellerId(sellerId).map(ReplicaSeller::getBusinessName);
    }

    @Override
    public Map<Long, String> findSellerNames(Collection<Long> sellerIds) {
        return sellerReplicaRepository.findAllBySellerIdIn(sellerIds).stream()
            .collect(Collectors.toMap(ReplicaSeller::getSellerId, ReplicaSeller::getBusinessName));
    }

    @Override
    public Optional<ChildView> findChild(Long childId) {
        return childReplicaRepository.findByChildId(childId)
            .map(child -> new ChildView(child.getChildId(), child.getMemberId(), child.isDeleted()));
    }

    private static ProductOptionView toView(ReplicaProduct product) {
        return new ProductOptionView(product.getOptionId(), product.getProductId(), product.getSellerId(),
            product.getProductName(), product.getOptionName(), product.getSize(), product.unitPrice(),
            product.isPurchasable());
    }
}
