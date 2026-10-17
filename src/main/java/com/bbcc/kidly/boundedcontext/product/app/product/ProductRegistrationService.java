package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageStorage;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterResult;
import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductInputReader;
import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductInputReader.InputLookup;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryOpening;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.InventoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.AddOptionsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.AddSizeSpecsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.OptionInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.PriceInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.RegisterInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SizeSpecInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUrls;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.SkuCodes;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductRegistrationService {

    private final CategoryRepository categoryRepository;
    private final ProductInputReader inputReader;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageStorage imageStorage;

    // 상품 · 옵션 · 실측을 판매대기 상태로 등록하고 옵션마다 초기 재고를 연다
    @Transactional
    public ProductRegisterResult register(Long sellerId, ProductRegisterRequest request) {
        Category category = categoryRepository.getById(request.categoryId());
        ProductType type = category.productTypeToRegister();
        InputLookup lookup = inputReader.readInputs(type);
        // 썸네일과 이미지 목록은 판매자 본인이 업로드 주소로 올린 이미지만 받는다
        ProductImageUrls.validate(
            request.thumbnailUrl(), request.imageUrls(), imageStorage.ownImageUrlPrefix(sellerId));

        Product product = Product.register(new RegisterInfo(
            sellerId,
            category,
            request.name(),
            request.brand(),
            request.description(),
            request.thumbnailUrl(),
            request.imageUrls(),
            inputReader.toInfoValues(request.attributes(), lookup)));
        // 옵션 · 실측 목록이 비면 추가 메서드가 IllegalArgumentException(500)을 던지므로 부르지 않고,
        // 빠진 입력은 아래 판매 시작 입력 검사의 400이 알려 준다
        List<ProductOption> options = List.of();
        if (!request.options().isEmpty()) {
            List<OptionInfo> optionInfos = inputReader.toOptionInfos(request.options(), lookup);
            List<PriceInfo> priceInfos = inputReader.toPriceInfos(request.prices(), lookup);
            options = product.addOptions(new AddOptionsInfo(sellerId, optionInfos, priceInfos), type);
            // 요청 옵션 자체의 검사를 먼저 하고, 다른 상품을 읽어야 하는 SKU 코드 검사는 그 뒤에 한다.
            // 상품은 아래에서 저장하므로 여기서 거절되면 아무것도 저장되지 않는다
            List<String> skuCodes = options.stream().map(ProductOption::getSkuCode).toList();
            SkuCodes.validateUnique(skuCodes, productRepository.findSkuCodesInUse(sellerId, skuCodes));
        }
        if (!request.sizeSpecs().isEmpty()) {
            List<SizeSpecInfo> specs = inputReader.toSizeSpecInfos(request.sizeSpecs(), lookup);
            product.addSizeSpecs(new AddSizeSpecsInfo(sellerId, specs), category, type);
        }
        product.validateSaleStartInputs(category, type);
        productRepository.save(product);

        List<ProductRegisterResult.Option> results = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            ProductOption option = options.get(i);
            int initialQuantity = request.options().get(i).initialQuantity();
            inventoryRepository.saveOpening(InventoryOpening.open(option, initialQuantity, sellerId));
            results.add(new ProductRegisterResult.Option(
                option.getId(),
                option.getName(),
                option.getPrice(),
                initialQuantity,
                option.getSaleStatus().name(),
                option.getSkuCode()));
        }
        return new ProductRegisterResult(
            product.getId(), product.getStatus().name(), product.getVersion(), results);
    }
}
