package com.bbcc.kidly.boundedcontext.notification.app;

import com.bbcc.kidly.boundedcontext.notification.domain.ProductName;
import com.bbcc.kidly.boundedcontext.notification.domain.ProductNameRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 문구에 쓸 상품 이름 복사본을 저장하고 찾는다.
 * 같은 상품의 이벤트는 같은 파티션(키 = 상품 ID)으로 한 건씩 오므로 같은 행을 동시에 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProductNameService {

    private final ProductNameRepository productNameRepository;

    @Transactional
    public void save(Long productId, String name, LocalDateTime occurredAt) {
        productNameRepository.findById(productId).ifPresentOrElse(
            productName -> productName.applyIfNewer(name, occurredAt),
            () -> productNameRepository.save(ProductName.create(productId, name, occurredAt)));
    }

    // 못 찾으면(아직 상품 이벤트를 받지 못했다) 부르는 쪽이 이름 없는 문구를 쓴다
    @Transactional(readOnly = true)
    public Optional<String> findName(Long productId) {
        return productNameRepository.findById(productId).map(ProductName::getName);
    }
}
