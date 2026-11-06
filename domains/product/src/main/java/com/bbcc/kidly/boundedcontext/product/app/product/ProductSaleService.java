package com.bbcc.kidly.boundedcontext.product.app.product;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductSaleStatusResult;
import com.bbcc.kidly.boundedcontext.product.app.product.support.ProductChangedPublisher;
import com.bbcc.kidly.boundedcontext.product.domain.product.Product;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductSaleService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductChangedPublisher productChangedPublisher;

    // 판매 상태가 바뀌었을 때만 상품 정보 변경됨을 보낸다. 이미 그 상태면 같은 요청을 다시 받은 것이라 이벤트를 보내지 않는다.
    // 같은 상품에 요청이 동시에 오면 product.version 충돌로 하나만 저장되고(나머지는 409), 이벤트도 커밋된 트랜잭션에서만 나간다.
    @Transactional
    public ProductSaleStatusResult changeSaleStatus(Long sellerId, Long productId, ProductStatus status) {
        Product product = findProduct(productId);
        Category category = findCategory(product);
        ProductType productType = category.productTypeToRegister();

        boolean changed = false;
        if (status == ProductStatus.ON_SALE) {
            changed = product.startSale(sellerId, category, productType);
        } else if (status == ProductStatus.STOPPED) {
            changed = product.stopSale(sellerId);
        }
        if (changed) {
            // 늘어난 version을 응답에 담으려고 바로 DB에 반영한다. 다른 요청이 먼저 고쳤으면 여기서 충돌해 이벤트를 만들지 않는다
            productRepository.saveAndFlush(product);
            productChangedPublisher.publish(product, category);
        }
        return toResult(product);
    }

    // 관리자는 상품을 등록한 판매자와 상관없이 판매 상태를 바꾼다. 판매 상태가 그대로여도 관리자 판매중지 표시 칸은 바뀔 수 있어
    // (판매자가 이미 판매중지한 상품을 관리자가 판매중지하는 경우) 항상 저장해 응답의 version을 DB와 맞춘다.
    // 상품 정보 변경됨은 판매 상태가 바뀌었을 때만 보낸다.
    @Transactional
    public ProductSaleStatusResult changeSaleStatusByAdmin(Long productId, ProductStatus status) {
        Product product = findProduct(productId);
        Category category = findCategory(product);
        ProductType productType = category.productTypeToRegister();

        boolean changed = false;
        if (status == ProductStatus.ON_SALE) {
            changed = product.startSaleByAdmin(category, productType);
        } else if (status == ProductStatus.STOPPED) {
            changed = product.stopSaleByAdmin();
        }
        productRepository.saveAndFlush(product);
        if (changed) {
            productChangedPublisher.publish(product, category);
        }
        return toResult(product);
    }

    // 판매자가 상품을 등록 취소한다. 이미 등록 취소한 상품이면 같은 요청을 다시 받은 것이라 바꾸지 않고 성공하며 이벤트도 보내지 않는다.
    // 판매대기에서 바로 등록 취소한 상품도 받는 쪽이 팔 수 없는 상품으로 알아야 하므로 상품 정보 변경됨을 보낸다
    @Transactional
    public ProductSaleStatusResult delist(Long sellerId, Long productId) {
        Product product = findProduct(productId);
        if (product.delist(sellerId)) {
            productRepository.saveAndFlush(product);
            publishChanged(product);
        }
        return toResult(product);
    }

    // 관리자가 등록 취소한 상품을 판매중지로 되돌린다. 되돌리면 늘 상태가 바뀌므로 늘 상품 정보 변경됨을 보낸다
    @Transactional
    public ProductSaleStatusResult revertDelistByAdmin(Long productId) {
        Product product = findProduct(productId);
        product.revertDelistByAdmin();
        productRepository.saveAndFlush(product);
        publishChanged(product);
        return toResult(product);
    }

    private void publishChanged(Product product) {
        Category category = findCategory(product);
        productChangedPublisher.publish(product, category);
    }

    private Product findProduct(Long productId) {
        return productRepository.getById(productId);
    }

    private Category findCategory(Product product) {
        return categoryRepository.getById(product.getCategoryId());
    }

    private ProductSaleStatusResult toResult(Product product) {
        return new ProductSaleStatusResult(product.getId(), product.getStatus(), product.getVersion());
    }
}
