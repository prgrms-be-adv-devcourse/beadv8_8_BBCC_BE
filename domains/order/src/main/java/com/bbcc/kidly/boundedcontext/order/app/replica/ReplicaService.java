package com.bbcc.kidly.boundedcontext.order.app.replica;

import com.bbcc.kidly.boundedcontext.order.app.port.CartOwnerCleaner;
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
    private final CartOwnerCleaner cartOwnerCleaner;

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
     * 장바구니는 비우지 않는다: 부르는 쪽이 조르기 철회를 알린 뒤 {@link #clearDeletedChildCart}로 비운다(#1001 · #1207).
     *
     * @return 반영 뒤 복사본이 삭제 상태인지(정리 대상)
     */
    public boolean applyChildChanged(ChildChangedCommand command) {
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
        return isChildDeleted(command.childId());
    }

    /**
     * 키 · 몸무게 없이 온 자녀 삭제. 복사본이 있으면 늦은 이벤트일 때 삭제 표시만, 없으면 삭제 복사본을 남긴다.
     * 장바구니는 비우지 않는다: 부르는 쪽이 조르기 철회를 알린 뒤 {@link #clearDeletedChildCart}로 비운다(#1207).
     *
     * @return 반영 뒤 복사본이 삭제 상태인지
     */
    public boolean applyChildDeleted(Long childId, Long memberId, LocalDateTime occurredAt) {
        replicaUpdateLock.acquire(ReplicaUpdateLock.Kind.CHILD, childId);
        childReplicaRepository.findByChildId(childId)
            .ifPresentOrElse(
                replica -> {
                    if (!replica.markDeletedIfNewer(occurredAt)) {
                        log.debug("복사본 이벤트 무시: event=child.changed childId={} reason=not-newer", childId);
                    }
                },
                () -> childReplicaRepository.save(ReplicaChild.createDeleted(childId, memberId, occurredAt)));
        return isChildDeleted(childId);
    }

    /**
     * 자녀(#1001): 복사본이 삭제 상태면(이번 이벤트가 늦어 무시됐어도 이미 삭제면) 그 자녀 프로필이 주인인 장바구니를 비운다.
     * 더 최신의 정상 프로필로 되살아난 복사본이면 비우지 않는다(늦게 온 삭제가 지금 장바구니를 지우지 않게).
     * 조르기 철회(예약을 잠그고 푼다)를 알린 뒤에 부른다(#1207): 그러면 조르기 예약이 먼저 잠기고 풀려, 여기서 잠글 예약이 거의 없다.
     */
    public void clearDeletedChildCart(Long childId) {
        if (isChildDeleted(childId)) {
            int removed = cartOwnerCleaner.removeAll(CartOwnerCleaner.OwnerType.CHILD, childId);
            log.info("자녀 삭제 정리: childId={} cartItems={}", childId, removed);
        }
    }

    private boolean isChildDeleted(Long childId) {
        return childReplicaRepository.findByChildId(childId).map(ReplicaChild::isDeleted).orElse(false);
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
     * 삭제는 되돌지 않으므로 여러 번 · 순서가 바뀌어 와도 결과가 같다. 장바구니는 비우지 않는다: 부르는 쪽이 조르기 철회를 알린 뒤
     * {@link #clearDeletedAccountCart}로 비운다(#1207).
     *
     * @return 반영 뒤 계정 복사본이 삭제 상태인지
     */
    public boolean applyChildAccountChanged(ChildAccountChangedCommand command) {
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
        return childAccountReplicaRepository.findByAccountId(command.accountId())
            .map(ReplicaChildAccount::isDeleted)
            .orElse(false);
    }

    /**
     * 계정이 삭제 상태면 그 계정의 자녀 프로필이 주인인 장바구니를 비운다(#1001: 장바구니 주인은 프로필이고, 계정이 없으면 그 장바구니를
     * 쓸 사람이 없다. 계정을 다시 만들면 새 장바구니). 조르기 철회를 알린 뒤에 부른다(#1207, 조르기 예약을 먼저 잠그고 풀게).
     */
    public void clearDeletedAccountCart(Long accountId) {
        ReplicaChildAccount account = childAccountReplicaRepository.findByAccountId(accountId).orElse(null);
        if (account == null || !account.isDeleted()) {
            return;
        }
        int removed = cartOwnerCleaner.removeAll(CartOwnerCleaner.OwnerType.CHILD, account.getChildProfileId());
        log.info("자녀 계정 삭제 정리: accountId={} childId={} cartItems={}", accountId, account.getChildProfileId(),
            removed);
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
            command.occurredAt(),
            command.thumbnailUrl());
    }
}
