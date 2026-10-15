package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.boundedcontext.product.domain.product.Product.AddOptionsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.AddSizeSpecsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.OptionInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SaleInputsInfo;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product.SizeSpecInfo;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.ArrayList;
import java.util.List;

/**
 * 상품을 수정하기 전에 옵션 · 가격 · 실측과 카테고리 변경을 검사하고, 바꿔 넣을 옵션 · 실측을 미리 만든다.
 */
final class ProductRevision {

    /**
     * 받은 값으로 미리 만든 옵션과 실측이다. 상품에 붙이지 않았고, 검사를 통과하면 바꿔 넣을 때 그대로 쓴다.
     */
    record Previews(List<ProductOption> options, List<SizeSpec> sizeSpecs) {
    }

    private ProductRevision() {
    }

    // 받은 옵션 · 가격 · 실측을 등록과 같은 규칙으로 검사한다. 지금 옵션 · 실측은 모두 갈아끼우므로 없다고 보고,
    // 바꿔 넣은 뒤의 모습은 상품에 붙이지 않는 미리 보기로 만들어 검사한다. 거절되면 아무것도 바뀌지 않는다
    static Previews validateSaleInputs(Product product, SaleInputsInfo saleInputs, Category newCategory) {
        ProductType productType = newCategory.productTypeToRegister();
        Long sellerId = product.getSellerId();
        AddOptionsInfo addOptions = new AddOptionsInfo(sellerId, saleInputs.options(), saleInputs.prices());
        ProductOptions.requireNonNullValues(addOptions);
        ProductSizeSpecs.requireNonNullValues(new AddSizeSpecsInfo(sellerId, saleInputs.sizeSpecs()));
        // 등록처럼 옵션 · 실측 목록이 비면 그 목록의 검사는 건너뛰고, 빠진 입력은 아래 판매 시작 입력 검사가 400으로 알린다
        if (!saleInputs.options().isEmpty()) {
            ProductOptions.validateNewOptions(addOptions, List.of(), productType);
        }
        List<ProductOption> options = new ArrayList<>();
        for (OptionInfo option : saleInputs.options()) {
            options.add(ProductOptions.newOption(product, option, saleInputs.prices(), productType));
        }
        if (!saleInputs.sizeSpecs().isEmpty()) {
            ProductSizeSpecs.validateNewSpecs(saleInputs.sizeSpecs(), List.of(), options, newCategory, productType);
        }
        List<SizeSpec> sizeSpecs = new ArrayList<>();
        for (SizeSpecInfo spec : saleInputs.sizeSpecs()) {
            sizeSpecs.add(SizeSpec.create(product, spec));
        }
        SaleStartConditions.requireComplete(
            options, sizeSpecs, newCategory, productType, ProductErrorCode.SALE_START_INPUT_MISSING);
        return new Previews(options, sizeSpecs);
    }

    // 판매대기 상품의 옵션 · 실측을 받지 않고 카테고리만 바꿀 때 검사한다. 다른 유형으로 바꾸면 지금 옵션은 새 유형에 맞지 않으므로
    // 새 옵션 · 실측을 함께 받아야 한다 (OPT-06). 같은 유형이면 지금 옵션 · 실측이 새 카테고리의 판매 시작 입력을 채워야 한다
    static void validatePendingCategoryChange(Product product, Category newCategory) {
        ProductType newType = newCategory.productTypeToRegister();
        if (newType != product.category().productTypeToRegister()) {
            throw new BusinessException(ProductErrorCode.SALE_INPUTS_REQUIRED_FOR_TYPE_CHANGE);
        }
        SaleStartConditions.requireComplete(product.getOptions(), product.getSizeSpecs(), newCategory, newType,
            ProductErrorCode.SALE_START_INPUT_MISSING);
    }

    // 판매를 시작한 상품은 옵션 · 실측을 여기서 바꾸지 못하므로,
    // 지금 실측이 새 카테고리의 필수 실측을 모두 채워야 한다 (SIZE-04)
    static void validateSoldCategoryChange(Product product, Category newCategory) {
        ProductType newType = newCategory.productTypeToRegister();
        // 다른 유형으로 바꾸면 기존 옵션을 모두 보관해야 하는데, 이번에는 판매를 시작한 상품에서 하지 않는다 (OPT-06)
        if (newType != product.category().productTypeToRegister()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_TYPE_CHANGE_NOT_ALLOWED);
        }
        SaleStartConditions.requireComplete(product.getOptions(), product.getSizeSpecs(), newCategory, newType,
            ProductErrorCode.MEASUREMENTS_MISSING_FOR_CATEGORY);
    }

    // 미리 만든 옵션 · 실측이 지금 값과 다른지 본다
    static boolean differs(Product product, Previews previews) {
        return ProductOptionRevision.differs(product.getOptions(), previews.options())
            || ProductSizeSpecs.differs(product.getSizeSpecs(), previews.sizeSpecs());
    }
}
