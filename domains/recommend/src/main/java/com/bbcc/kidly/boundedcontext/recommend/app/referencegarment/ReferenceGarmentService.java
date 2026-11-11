package com.bbcc.kidly.boundedcontext.recommend.app.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.app.port.referencegarment.GarmentCategoryPaths;
import com.bbcc.kidly.boundedcontext.recommend.app.port.purchase.PurchasedGarmentRow;
import com.bbcc.kidly.boundedcontext.recommend.app.port.purchase.PurchasedGarments;
import com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto.PurchasedGarmentResult;
import com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto.ReferenceGarmentResult;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentPart;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarment;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.ReferenceGarmentSpec;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendErrorCode;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChildRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaSizeSpec;
import com.bbcc.kidly.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReferenceGarmentService {

    private final ReferenceGarmentRepository referenceGarmentRepository;

    private final GarmentCategoryPaths garmentCategoryPaths;

    private final RecommendReplicaChildRepository replicaChildRepository;

    private final PurchasedGarments purchasedGarments;

    private final RecommendReplicaProductRepository replicaProductRepository;

    @Transactional(readOnly = true)
    public List<ReferenceGarmentResult> list(Long memberId, Long childId) {
        requireChildOf(memberId, childId);
        return referenceGarmentRepository.findAllByChildProfileId(childId).stream()
            .map(ReferenceGarmentResult::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<PurchasedGarmentResult> listPurchasedGarments(Long memberId, Long childId, int page, int size) {
        requireChildOf(memberId, childId);
        Page<PurchasedGarmentRow> rows = purchasedGarments.findByChild(childId, PageRequest.of(page, size));
        // 줄마다 읽지 않도록 그 페이지의 상품을 한 번에 읽는다
        Map<Long, RecommendReplicaProduct> products = new HashMap<>();
        for (RecommendReplicaProduct product : replicaProductRepository.findAllById(productIdsOf(rows))) {
            products.put(product.getProductId(), product);
        }
        return rows.map(row -> toResult(row, products.get(row.productId())));
    }

    private static Set<Long> productIdsOf(Page<PurchasedGarmentRow> rows) {
        Set<Long> productIds = new HashSet<>();
        for (PurchasedGarmentRow row : rows) {
            productIds.add(row.productId());
        }
        return productIds;
    }

    private static PurchasedGarmentResult toResult(PurchasedGarmentRow row, RecommendReplicaProduct product) {
        if (product == null) {
            return new PurchasedGarmentResult(row.orderItemId(), row.productId(), null, null, row.size(), null,
                row.confirmedAt());
        }
        Map<String, BigDecimal> measurements = null;
        for (RecommendReplicaSizeSpec spec : product.getSizeSpecs()) {
            if (spec.getSize().equals(row.size())) {
                measurements = spec.getMeasurements();
            }
        }
        return new PurchasedGarmentResult(row.orderItemId(), row.productId(), product.getName(),
            product.getCategoryId(), row.size(), measurements, row.confirmedAt());
    }

    @Transactional
    public ReferenceGarmentResult save(Long memberId, Long childId, String partName, Long categoryId,
        Map<String, BigDecimal> measurements) {
        GarmentPart part = GarmentPart.from(partName);
        requireChildOf(memberId, childId);
        ReferenceGarmentSpec spec = ReferenceGarmentSpec.of(
            part, categoryId, garmentCategoryPaths.findPathOf(categoryId), measurements);
        Optional<ReferenceGarment> existing = referenceGarmentRepository.findByChildProfileIdAndPart(childId, part);
        if (existing.isPresent()) {
            existing.get().replace(spec);
            return ReferenceGarmentResult.from(existing.get());
        }
        return ReferenceGarmentResult.from(referenceGarmentRepository.save(ReferenceGarment.create(childId, spec)));
    }

    @Transactional
    public void delete(Long memberId, Long childId, String partName) {
        GarmentPart part = GarmentPart.from(partName);
        requireChildOf(memberId, childId);
        referenceGarmentRepository.deleteByChildProfileIdAndPart(childId, part);
    }

    // 사이즈 추천은 남의 자녀도 없는 자녀처럼 404로 답하지만, 이 API는 남의 자녀를 403으로 구분한다
    private void requireChildOf(Long memberId, Long childId) {
        RecommendReplicaChild child = replicaChildRepository.findById(childId)
            .orElseThrow(() -> new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND));
        if (child.isDeleted()) {
            throw new BusinessException(RecommendErrorCode.CHILD_NOT_FOUND);
        }
        if (!child.isVisibleTo(memberId)) {
            throw new BusinessException(ReferenceGarmentErrorCode.CHILD_FORBIDDEN);
        }
    }
}
