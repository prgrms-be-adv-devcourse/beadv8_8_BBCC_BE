package com.bbcc.kidly.boundedcontext.recommend.app.replica;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.ProductEmbeddingText;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProductRepository;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RecommendReplicaProductService {

    // 상품 옵션 값 중 색상의 코드다
    private static final String COLOR_CODE = "COLOR";

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
    // 옵션에서는 사이즈 · 권장 키 · 가격 · 품절 · 판매 상태를 옮긴다(옵션 이름과 옵션 값은 추천이 쓰지 않는다).
    // 브랜드 · 설명 · 사이즈별 실측 · 카테고리 경로도 그대로 옮긴다
    private RecommendReplicaProduct.ChangeInfo toChangeInfo(ProductChangedEvent event) {
        ProductChangedEvent.Data data = event.data();
        return new RecommendReplicaProduct.ChangeInfo(
            data.productId(),
            data.sellerId(),
            data.categoryId(),
            data.name(),
            data.status(),
            data.thumbnailUrl(),
            data.createdAt(),
            data.sizeFree(),
            toOptionInfos(data.options()),
            data.brand(),
            data.description(),
            toSizeSpecInfos(data.sizeSpecs()),
            toInfoValues(data.infoValues()),
            event.occurredAt(),
            toCategoryPathEntryInfos(data.categoryPath()),
            data.saleStartedAt(),
            toColors(data.options()));
    }

    // 이벤트의 카테고리 경로도 칸이 없는 예전 메시지는 빈 목록으로 오므로 null을 검사하지 않는다
    private List<RecommendReplicaProduct.CategoryPathEntryInfo> toCategoryPathEntryInfos(
        List<ProductChangedEvent.CategoryPathEntry> categoryPath) {
        List<RecommendReplicaProduct.CategoryPathEntryInfo> infos = new ArrayList<>();
        for (ProductChangedEvent.CategoryPathEntry entry : categoryPath) {
            infos.add(new RecommendReplicaProduct.CategoryPathEntryInfo(entry.id(), entry.name()));
        }
        return infos;
    }

    // 이벤트의 상품 정보 값은 칸이 없는 예전 메시지에서 null로 오므로 빈 목록으로 본다
    // 옵션 값 중 색상(COLOR)만 중복 없이 모은다. 옵션 값이 없는 예전 메시지는 빈 목록이다
    private List<String> toColors(List<ProductChangedEvent.Option> options) {
        Set<String> colors = new LinkedHashSet<>();
        if (options == null) {
            return List.of();
        }
        for (ProductChangedEvent.Option option : options) {
            if (option.values() == null) {
                continue;
            }
            for (ProductChangedEvent.AttributeEntry value : option.values()) {
                if (COLOR_CODE.equals(value.code()) && value.value() != null && !value.value().isBlank()) {
                    colors.add(value.value());
                }
            }
        }
        return List.copyOf(colors);
    }

    private List<ProductEmbeddingText.InfoValue> toInfoValues(List<ProductChangedEvent.AttributeEntry> infoValues) {
        List<ProductEmbeddingText.InfoValue> result = new ArrayList<>();
        if (infoValues == null) {
            return result;
        }
        for (ProductChangedEvent.AttributeEntry entry : infoValues) {
            result.add(new ProductEmbeddingText.InfoValue(entry.code(), entry.value()));
        }
        return result;
    }

    // 이벤트의 사이즈별 실측은 칸이 없는 예전 메시지도 빈 목록으로 오므로 null을 검사하지 않는다
    private List<RecommendReplicaProduct.SizeSpecInfo> toSizeSpecInfos(List<ProductChangedEvent.SizeSpec> sizeSpecs) {
        List<RecommendReplicaProduct.SizeSpecInfo> infos = new ArrayList<>();
        for (ProductChangedEvent.SizeSpec spec : sizeSpecs) {
            infos.add(new RecommendReplicaProduct.SizeSpecInfo(
                spec.size(), spec.minHeightCm(), spec.maxHeightCm(), spec.measurements()));
        }
        return infos;
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
                option.size(), option.minHeightCm(), option.maxHeightCm(),
                option.price(), option.soldOut(), option.saleStatus()));
        }
        return infos;
    }
}
