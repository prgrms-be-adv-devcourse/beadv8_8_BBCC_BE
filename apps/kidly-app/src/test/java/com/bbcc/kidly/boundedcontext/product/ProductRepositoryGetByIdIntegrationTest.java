package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.AttributeValueRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.CategoryRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.MeasurementItemRepository;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeErrorCode;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductTypeRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.ErrorCode;
import com.bbcc.kidly.support.IntegrationTest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * 상품 도메인 리포지토리의 getById가 없는 ID에 그 대상의 404 오류를 던지는지 실제 PostgreSQL로 확인한다.
 * JpaRepository에도 같은 이름의 getById(프록시를 돌려줌)가 있어, Jpa 구현이 도메인 쪽 메서드를 고르는지 함께 확인한다.
 */
@IntegrationTest
class ProductRepositoryGetByIdIntegrationTest {

    private static final Long UNKNOWN_ID = 999_999L;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AttributeRepository attributeRepository;

    @Autowired
    private AttributeValueRepository attributeValueRepository;

    @Autowired
    private ProductTypeRepository productTypeRepository;

    @Autowired
    private MeasurementItemRepository measurementItemRepository;

    @Test
    @DisplayName("없는 ID로 getById를 부르면 대상마다 정한 404 오류를 던진다")
    void getByIdRejectsUnknownId() {
        assertNotFound(() -> productRepository.getById(UNKNOWN_ID), ProductErrorCode.PRODUCT_NOT_FOUND);
        assertNotFound(() -> categoryRepository.getById(UNKNOWN_ID), CategoryErrorCode.CATEGORY_NOT_FOUND);
        assertNotFound(() -> attributeRepository.getById(UNKNOWN_ID), AttributeErrorCode.ATTRIBUTE_NOT_FOUND);
        assertNotFound(() -> attributeValueRepository.getById(UNKNOWN_ID),
            AttributeErrorCode.ATTRIBUTE_VALUE_NOT_FOUND);
        assertNotFound(() -> productTypeRepository.getById(UNKNOWN_ID), ProductTypeErrorCode.PRODUCT_TYPE_NOT_FOUND);
        assertNotFound(() -> measurementItemRepository.getById(UNKNOWN_ID),
            MeasurementItemErrorCode.MEASUREMENT_ITEM_NOT_FOUND);
    }

    private static void assertNotFound(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(expected);
            assertThat(e.getErrorCode().status()).isEqualTo(HttpStatus.NOT_FOUND);
        });
    }
}
