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
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductRegistrationService {

    private final CategoryRepository categoryRepository;
    private final ProductTypeRepository productTypeRepository;
    private final ProductInputReader inputReader;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageStorage imageStorage;

    @Transactional
    public ProductRegisterResult register(Long sellerId, ProductRegisterRequest request) {
        var category = categoryRepository.getByIdForShare(request.categoryId());
        var type = productTypeRepository.getByIdForShare(category.productTypeToRegister().getId());
        InputLookup lookup = inputReader.readInputs(type);
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
        List<ProductOption> options = List.of();
        if (!request.options().isEmpty()) {
            List<OptionInfo> optionInfos = inputReader.toOptionInfos(request.options(), lookup);
            List<PriceInfo> priceInfos = inputReader.toPriceInfos(request.prices(), lookup);
            options = product.addOptions(new AddOptionsInfo(sellerId, optionInfos, priceInfos), type);
            List<String> skuCodes = options.stream().map(ProductOption::getSkuCode).toList();
            SkuCodes.validateUnique(skuCodes, productRepository.findSkuCodesInUse(sellerId, skuCodes));
        }
        if (!request.sizeSpecs().isEmpty()) {
            List<SizeSpecInfo> specs = inputReader.toSizeSpecInfos(request.sizeSpecs(), lookup);
            product.addSizeSpecs(new AddSizeSpecsInfo(sellerId, specs), category, type);
        }
        product.validateSaleStartInputs(category, type);
        productRepository.save(product);

        var results = new ArrayList<ProductRegisterResult.Option>();
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
