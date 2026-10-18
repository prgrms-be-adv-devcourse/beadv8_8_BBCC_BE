package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageStorage;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductUpdateRequest;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.SellerProductDetailResult;
import com.bbcc.kidly.boundedcontext.product.app.product.support.InventoryReplacer;
import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductChangedPublisher;
import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductInputReader.InputLookup;
import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductInputReader;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.OptionInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SaleInputsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.UpdateInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.UpdateResult;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUrls;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.SkuCodes;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자가 상품의 정보를 고치는 유스케이스. 업무 규칙은 Product.update가 지키고, 여기서는 읽고 넘기고 저장한다.
 */
@Service
@RequiredArgsConstructor
public class ProductUpdateService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductTypeRepository productTypeRepository;
    private final ProductInputReader inputReader;
    private final InventoryRepository inventoryRepository;
    private final InventoryReplacer inventoryReplacer;
    private final ProductChangedPublisher productChangedPublisher;
    private final SellerProductService sellerProductService;
    private final ProductImageStorage imageStorage;

    // 상품을 고치고, 바뀌었으면 version을 올려 저장한 뒤 판매대기가 아닌 상품이면 상품 정보 변경됨을 발행한다
    @Transactional
    public SellerProductDetailResult update(Long sellerId, Long productId, ProductUpdateRequest request) {
        Product product = productRepository.getById(productId);
        // 관리자 설정 변경이 커밋되기 전이면 기다렸다가 바뀐 설정으로 검사한다(TYPE-07). 잠금 순서는 카테고리 → 유형이다
        Category category = categoryRepository.getByIdForShare(request.categoryId());
        ProductType type = productTypeRepository.getByIdForShare(category.productTypeToRegister().getId());
        InputLookup lookup = inputReader.readInputs(type);

        UpdateInfo info = new UpdateInfo(
            sellerId,
            request.version(),
            category,
            request.name(),
            request.brand(),
            request.description(),
            request.thumbnailUrl(),
            request.imageUrls(),
            inputReader.toInfoValues(request.attributes(), lookup));
        SaleInputsInfo saleInputs = toSaleInputs(request, lookup);
        // 다른 상품과 SKU 코드가 겹치는지는 상품을 고치기 전에 본다. 고친 뒤에 조회하면 그 조회가 옵션 삭제를 먼저 flush해
        // 재고 행이 옵션을 가리킨 채로 막힌다. 고치는 상품 자신이 이미 쓰는 코드는 겹친 것으로 보지 않는다 (OPT-07)
        if (saleInputs != null) {
            List<String> skuCodes = new ArrayList<>();
            for (OptionInfo option : saleInputs.options()) {
                skuCodes.add(option.skuCode());
            }
            SkuCodes.validateUnique(
                skuCodes, productRepository.findSkuCodesInUseByOtherProducts(sellerId, productId, skuCodes));
        }
        // 이미지는 판매자 본인이 올린 것만 받는다. 다른 판매자의 요청이 이미지 400을 먼저 받지 않도록 등록한 판매자부터 확인하고(403),
        // 상품을 고치기 전에 검사해 잘못된 이미지로는 상품이 바뀌지 않게 한다 (update 안에서 등록한 판매자를 한 번 더 확인한다)
        product.validateOwner(sellerId);
        ProductImageUrls.validate(
            request.thumbnailUrl(), request.imageUrls(), imageStorage.ownImageUrlPrefix(sellerId));
        List<ProductOption> optionsBefore = List.copyOf(product.getOptions());
        UpdateResult result = product.update(info, saleInputs);
        // 뺀 옵션의 재고는 상품을 고친 뒤 첫 flush보다 먼저 지운다. 옵션 삭제가 먼저 반영되면 재고 행이 옵션을 가리켜 막힌다
        inventoryRepository.deleteByOptionIds(idsOf(result.removedOptions()));
        if (result.changed()) {
            productRepository.incrementVersion(product);
            // 새 옵션의 ID는 저장할 때 생기므로, 재고를 여는 일은 저장한 뒤에 한다
            productRepository.saveAndFlush(product);
        }
        // 초기 재고는 재고 표에 있어 상품이 그대로여도 바뀔 수 있으므로, 받은 옵션이 있으면 늘 맞춘다
        if (!result.requestedOptions().isEmpty()) {
            inventoryReplacer.align(result.requestedOptions(), optionsBefore, request.options(), sellerId);
        }
        if (result.changed() && product.getStatus() != ProductStatus.SALE_PENDING) {
            productChangedPublisher.publish(product, type);
        }
        return sellerProductService.findMyProductDetail(sellerId, productId);
    }

    private List<Long> idsOf(List<ProductOption> options) {
        List<Long> ids = new ArrayList<>();
        for (ProductOption option : options) {
            ids.add(option.getId());
        }
        return ids;
    }

    // 옵션 · 가격 · 실측을 모두 보내지 않았으면 바꾸지 않는다는 뜻으로 null을 돌려준다
    private SaleInputsInfo toSaleInputs(ProductUpdateRequest request, InputLookup lookup) {
        if (request.options() == null && request.prices() == null && request.sizeSpecs() == null) {
            return null;
        }
        // 옵션 · 가격 · 실측은 셋을 함께 바꿔 넣으므로, 일부만 오면 무엇을 바꾸려는 요청인지 알 수 없어 거절한다
        if (request.options() == null || request.prices() == null || request.sizeSpecs() == null) {
            throw new BusinessException(ProductErrorCode.SALE_INPUTS_INCOMPLETE);
        }
        return new SaleInputsInfo(
            inputReader.toOptionInfos(request.options(), lookup),
            inputReader.toPriceInfos(request.prices(), lookup),
            inputReader.toSizeSpecInfos(request.sizeSpecs(), lookup));
    }
}
