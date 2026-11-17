package com.bbcc.kidly.boundedcontext.product.app.replica;

import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSeller;
import com.bbcc.kidly.boundedcontext.product.domain.replica.ProductReplicaSellerRepository;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductReplicaSellerService {

    private final ProductReplicaSellerRepository replicaSellerRepository;

    public void applySellerChanged(SellerChangedEvent event) {
        ProductReplicaSeller.ChangeInfo info = toChangeInfo(event);
        Optional<ProductReplicaSeller> stored = replicaSellerRepository.findById(info.sellerId());
        if (stored.isEmpty()) {
            replicaSellerRepository.save(ProductReplicaSeller.create(info));
            return;
        }
        stored.get().applyIfNewer(info);
    }

    private ProductReplicaSeller.ChangeInfo toChangeInfo(SellerChangedEvent event) {
        SellerChangedEvent.Data data = event.data();
        return new ProductReplicaSeller.ChangeInfo(
            data.sellerId(), data.businessName(), data.status(), event.occurredAt());
    }
}
