package com.bbcc.kidly.boundedcontext.order.app.replica;

import com.bbcc.kidly.boundedcontext.order.app.port.ChildAccountReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ChildReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ReplicaUpdateLock;
import com.bbcc.kidly.boundedcontext.order.app.port.SellerReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildAccountChangedCommand;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildChangedCommand;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ProductChangedCommand;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.SellerChangedCommand;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChild;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaChildAccount;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaSeller;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 · 자녀 · 판매자 변경을 복사본에 반영한다. 늦게 발생한 변경만 덮어쓴다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReplicaService {

    private final ReplicaUpdateLock replicaUpdateLock;
    private final ProductReplicaRepository productReplicaRepository;
    private final ChildReplicaRepository childReplicaRepository;
    private final SellerReplicaRepository sellerReplicaRepository;
    private final ChildAccountReplicaRepository childAccountReplicaRepository;

    /**
     * 옵션마다 없으면 만들고 있으면 늦은 변경일 때만 덮어쓴다. 변경에서 빠진 옵션은 행을 남기고 품절로 바꾼다.
     */
    public void applyProductChanged(ProductChangedCommand command) {
        replicaUpdateLock.acquire(ReplicaUpdateLock.Kind.PRODUCT, command.productId());
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
        replicaUpdateLock.acquire(ReplicaUpdateLock.Kind.CHILD, command.childId());
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

    /**
     * 키 · 몸무게 없이 온 자녀 삭제. 복사본이 있으면 늦은 이벤트일 때 삭제 표시만, 없으면 삭제 복사본을 남긴다.
     */
    public void applyChildDeleted(Long childId, Long memberId, LocalDateTime occurredAt) {
        replicaUpdateLock.acquire(ReplicaUpdateLock.Kind.CHILD, childId);
        childReplicaRepository.findByChildId(childId)
            .ifPresentOrElse(
                replica -> {
                    if (!replica.markDeletedIfNewer(occurredAt)) {
                        log.debug("복사본 이벤트 무시: event=child.changed childId={} reason=not-newer", childId);
                    }
                },
                () -> childReplicaRepository.save(ReplicaChild.createDeleted(childId, memberId, occurredAt)));
    }

    /**
     * 없으면 만들고 있으면 늦은 변경일 때만 덮어쓴다.
     */
    public void applySellerChanged(SellerChangedCommand command) {
        replicaUpdateLock.acquire(ReplicaUpdateLock.Kind.SELLER, command.sellerId());
        ReplicaSeller.Values values = new ReplicaSeller.Values(
            command.sellerId(), command.businessName(), command.status(), command.occurredAt());
        sellerReplicaRepository.findBySellerId(command.sellerId())
            .ifPresentOrElse(
                replica -> {
                    if (!replica.applyIfNewer(values)) {
                        log.debug("복사본 이벤트 무시: event=seller.changed sellerId={} reason=not-newer",
                            command.sellerId());
                    }
                },
                () -> sellerReplicaRepository.save(ReplicaSeller.create(values)));
    }

    /**
     * 자녀 계정 복사본(#906): 없으면 만들고(삭제 메시지가 먼저 와도 삭제된 행으로), 있으면 삭제는 한 번 · 생성은 늦을 때만 반영한다.
     */
    public void applyChildAccountChanged(ChildAccountChangedCommand command) {
        replicaUpdateLock.acquire(ReplicaUpdateLock.Kind.CHILD_ACCOUNT, command.accountId());
        ReplicaChildAccount.Values values = new ReplicaChildAccount.Values(command.accountId(),
            command.childProfileId(), command.guardianId(), command.deleted(), command.occurredAt());
        childAccountReplicaRepository.findByAccountId(command.accountId())
            .ifPresentOrElse(
                replica -> {
                    if (!replica.applyIfNewer(values)) {
                        log.debug("복사본 이벤트 무시: event=child-account.changed accountId={} reason={}",
                            command.accountId(), replica.isDeleted() ? "already-deleted" : "not-newer");
                    }
                },
                () -> childAccountReplicaRepository.save(ReplicaChildAccount.create(values)));
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
