package com.bbcc.kidly.boundedcontext.recommend.app.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RecommendReplicaProductService {

    private final RecommendReplicaProductRepository replicaProductRepository;

    /**
     * 상품 정보 변경됨 이벤트의 값을 상품 복사본에 반영한다.
     * 처음 받은 상품이면 복사본을 만들고, 이미 있으면 저장된 시각보다 늦게 일어난 이벤트일 때만 덮어쓴다.
     */
    public void applyProductChanged(ProductChangedEvent event) {
        RecommendReplicaProduct.ChangeInfo info = toChangeInfo(event);
        Optional<RecommendReplicaProduct> stored = replicaProductRepository.findById(info.productId());
        if (stored.isEmpty()) {
            replicaProductRepository.save(RecommendReplicaProduct.create(info));
            return;
        }
        stored.get().applyIfNewer(info);
    }

    // 이벤트의 상품 등록 시각(createdAt)은 감사 칸과 겹치지 않게 복사본의 상품 등록 시각으로 옮기고,
    // 옵션에서는 사이즈와 권장 키만 옮긴다(옵션 이름 · 판매 상태 · 품절은 추천이 쓰지 않는다)
    private RecommendReplicaProduct.ChangeInfo toChangeInfo(ProductChangedEvent event) {
        ProductChangedEvent.Data data = event.data();
        return new RecommendReplicaProduct.ChangeInfo(
            data.productId(),
            data.sellerId(),
            data.categoryId(),
            data.status(),
            data.thumbnailUrl(),
            data.createdAt(),
            data.sizeFree(),
            toOptionInfos(data.options()),
            event.occurredAt());
    }

    // 옵션이 없으면 null을 그대로 넘겨, 값 검사를 엔티티가 맡도록 한다
    private List<RecommendReplicaProduct.OptionInfo> toOptionInfos(List<ProductChangedEvent.Option> options) {
        if (options == null) {
            return null;
        }
        List<RecommendReplicaProduct.OptionInfo> infos = new ArrayList<>();
        for (ProductChangedEvent.Option option : options) {
            if (option == null) {
                infos.add(null);
                continue;
            }
            infos.add(new RecommendReplicaProduct.OptionInfo(
                option.size(), option.minHeightCm(), option.maxHeightCm()));
        }
        return infos;
    }
}
