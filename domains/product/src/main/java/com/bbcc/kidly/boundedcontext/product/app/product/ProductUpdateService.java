package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageStorage;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest.OptionInput;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductUpdateRequest;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.SellerProductDetailResult;
import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductChangedPublisher;
import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductInputReader.InputLookup;
import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductInputReader;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentReason;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord.AdjustInfo;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryAdjustmentRecord;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.OptionInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SaleInputsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.UpdateInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.UpdateResult;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUrls;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.SkuCodes;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductUpdateService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductTypeRepository productTypeRepository;
    private final ProductInputReader inputReader;
    private final InventoryRepository inventoryRepository;
    private final ProductChangedPublisher productChangedPublisher;
    private final SellerProductService sellerProductService;
    private final ProductImageStorage imageStorage;

    @Transactional
    public SellerProductDetailResult update(Long sellerId, Long productId, ProductUpdateRequest request) {
        var product = productRepository.getById(productId);
        var category = categoryRepository.getByIdForShare(request.categoryId());
        var type = productTypeRepository.getByIdForShare(category.productTypeToRegister().getId());
        InputLookup lookup = inputReader.readInputs(type);

        var info = new UpdateInfo(
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
        validateSkuCodesBeforeChangingProduct(sellerId, productId, saleInputs);
        product.validateOwner(sellerId);
        ProductImageUrls.validate(
            request.thumbnailUrl(), request.imageUrls(), imageStorage.ownImageUrlPrefix(sellerId));

        List<ProductOption> optionsBefore = List.copyOf(product.getOptions());
        UpdateResult result = product.update(info, saleInputs);
        deleteInventoryBeforeOptionRowsFlush(result.removedOptions());
        if (result.changed()) {
            productRepository.incrementVersion(product);
            productRepository.saveAndFlush(product);
        }
        if (!result.requestedOptions().isEmpty()) {
            alignInventory(result.requestedOptions(), optionsBefore, request.options(), sellerId);
        }
        if (result.changed() && product.hasStartedSale()) {
            productChangedPublisher.publish(product, category);
        }
        return sellerProductService.findMyProductDetail(sellerId, productId);
    }

    private SaleInputsInfo toSaleInputs(ProductUpdateRequest request, InputLookup lookup) {
        if (request.options() == null && request.prices() == null && request.sizeSpecs() == null) {
            return null;
        }
        if (request.options() == null || request.prices() == null || request.sizeSpecs() == null) {
            throw new BusinessException(ProductErrorCode.SALE_INPUTS_INCOMPLETE);
        }
        return new SaleInputsInfo(
            inputReader.toOptionInfos(request.options(), lookup),
            inputReader.toPriceInfos(request.prices(), lookup),
            inputReader.toSizeSpecInfos(request.sizeSpecs(), lookup));
    }

    private void validateSkuCodesBeforeChangingProduct(Long sellerId, Long productId, SaleInputsInfo saleInputs) {
        if (saleInputs == null) {
            return;
        }
        List<String> skuCodes = saleInputs.options().stream().map(OptionInfo::skuCode).toList();
        SkuCodes.validateUnique(
            skuCodes, productRepository.findSkuCodesInUseByOtherProducts(sellerId, productId, skuCodes));
    }

    private void deleteInventoryBeforeOptionRowsFlush(List<ProductOption> removedOptions) {
        inventoryRepository.deleteByOptionIds(idsOf(removedOptions));
    }

    private void alignInventory(List<ProductOption> requestedOptions, List<ProductOption> optionsBefore,
        List<OptionInput> inputs, Long sellerId) {
        Set<ProductOption> before = Collections.newSetFromMap(new IdentityHashMap<>());
        before.addAll(optionsBefore);
        List<ProductOption> kept = requestedOptions.stream().filter(before::contains).toList();
        Map<Long, Integer> availableQty = inventoryRepository.findAvailableQtyByOptionId(idsOf(kept));
        var corrections = new ArrayList<Correction>();
        for (int i = 0; i < requestedOptions.size(); i++) {
            ProductOption option = requestedOptions.get(i);
            int initialQuantity = inputs.get(i).initialQuantity();
            if (!before.contains(option)) {
                inventoryRepository.saveOpening(InventoryOpening.open(option, initialQuantity, sellerId));
                continue;
            }
            int delta = initialQuantity - availableQty.getOrDefault(option.getId(), 0);
            if (delta != 0) {
                corrections.add(new Correction(option.getId(), delta, InventoryAdjustmentRecord.adjust(
                    new AdjustInfo(option, delta, InventoryAdjustmentReason.CORRECTION, sellerId))));
            }
        }
        applyCorrections(corrections);
    }

    private void applyCorrections(List<Correction> corrections) {
        for (Correction correction : corrections) {
            boolean adjusted = inventoryRepository.adjustIfAvailable(
                correction.optionId(), correction.delta(), LocalDateTime.now());
            if (!adjusted) {
                throw new IllegalStateException("옵션 " + correction.optionId() + "의 재고를 정정하지 못했습니다");
            }
            inventoryRepository.saveAdjustmentRecord(correction.record());
        }
    }

    private List<Long> idsOf(List<ProductOption> options) {
        return options.stream().map(ProductOption::getId).toList();
    }

    private record Correction(Long optionId, int delta, InventoryAdjustmentRecord record) {
    }
}
