package com.bbcc.kidly.boundedcontext.order.app.replica;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildChangedCommand;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ProductChangedCommand;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 · 자녀 변경을 복사본에 반영한다. 늦게 발생한 변경만 덮어쓴다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReplicaService {

    private final ProductReplicaRepository productReplicaRepository;
    private final ChildReplicaRepository childReplicaRepository;

    /**
     * 옵션마다 없으면 만들고 있으면 늦은 변경일 때만 덮어쓴다. 변경에서 빠진 옵션은 행을 남기고 품절로 바꾼다.
     */
    public void applyProductChanged(ProductChangedCommand command) {
        Map<Long, ReplicaProduct> stored = productReplicaRepository.findAllByProductId(command.productId())
            .stream()
            .collect(Collectors.toMap(ReplicaProduct::getOptionId, Function.identity()));
        for (ProductChangedCommand.Option option : command.options()) {
            ReplicaProduct.Values values = toValues(command, option);
            ReplicaProduct replica = stored.remove(option.optionId());
            if (replica == null) {
                productReplicaRepository.save(ReplicaProduct.create(values));
            } else if (!replica.applyIfNewer(values)) {
                log.debug("복사본 이벤트 무시: event=product.changed productId={} optionId={} reason=not-newer",
                    command.productId(), option.optionId());
            }
        }
        stored.values().forEach(missing -> missing.markSoldOutIfNewer(command.occurredAt()));
    }

    /**
     * 없으면 만들고 있으면 늦은 변경일 때만 덮어쓴다. 삭제도 행을 남기고 삭제 표시만 한다.
     */
    public void applyChildChanged(ChildChangedCommand command) {
        ReplicaChild.Values values = new ReplicaChild.Values(
            command.childId(),
            command.memberId(),
            command.name(),
            command.heightCm(),
            command.weightKg(),
            command.bodyType(),
            command.deleted(),
            command.occurredAt());
        childReplicaRepository.findByChildId(command.childId())
            .ifPresentOrElse(
                replica -> {
                    if (!replica.applyIfNewer(values)) {
                        log.debug("복사본 이벤트 무시: event=child.changed childId={} reason=not-newer",
                            command.childId());
                    }
                },
                () -> childReplicaRepository.save(ReplicaChild.create(values)));
    }

    private ReplicaProduct.Values toValues(ProductChangedCommand command, ProductChangedCommand.Option option) {
        return new ReplicaProduct.Values(
            option.optionId(),
            command.productId(),
            command.sellerId(),
            command.productName(),
            option.optionName(),
            option.size(),
            option.unitPrice(),
            command.categoryId(),
            command.productStatus(),
            option.saleStatus(),
            option.soldOut(),
            command.occurredAt());
    }
}
