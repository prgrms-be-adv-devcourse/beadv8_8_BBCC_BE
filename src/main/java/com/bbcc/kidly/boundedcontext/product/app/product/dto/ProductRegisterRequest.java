package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 판매자가 상품을 등록할 때 보내는 값이다. JSON의 문자열 ID("123")도 Long으로 받는다.
 *
 * @param categoryId 상품을 등록할 말단 카테고리
 * @param attributes 상품 정보 항목에 입력한 값
 * @param prices 가격 구분 묶음마다 하나씩이다. 가격 구분 항목이 없는 유형은 valueIds가 빈 목록인 하나만 보낸다
 * @param sizeSpecs 사이즈 값마다 하나씩이다. 사이즈가 없는 상품은 빈 목록이다
 */
public record ProductRegisterRequest(
    // 주문 도메인의 상품 복사본이 상품명을 100자 칸에 저장하므로 100자까지만 받는다. 255자는 DB 기본 varchar(255) 칸이라서다
    @NotBlank(message = "상품명을 입력해 주세요.")
    @Size(max = 100, message = "상품명은 100자 이하로 입력해 주세요.")
    String name,
    @Size(max = 255, message = "브랜드는 255자 이하로 입력해 주세요.")
    String brand,
    @NotNull(message = "카테고리를 골라 주세요.")
    Long categoryId,
    String description,
    String thumbnailUrl,
    @NotNull(message = "이미지 목록이 필요합니다.")
    List<String> imageUrls,
    @NotNull(message = "정보 항목 목록이 필요합니다.")
    List<@Valid AttributeInput> attributes,
    @NotNull(message = "옵션 목록이 필요합니다.")
    List<@Valid OptionInput> options,
    @NotNull(message = "가격 목록이 필요합니다.")
    List<@Valid PriceInput> prices,
    @NotNull(message = "실측 목록이 필요합니다.")
    List<@Valid SizeSpecInput> sizeSpecs) {

    /**
     * @param valueIds 목록 항목에서 고른 값. 직접 입력 항목이면 비워 둔다
     * @param text 직접 입력 항목의 값. 목록 항목이면 null이다
     */
    public record AttributeInput(Long attributeId, List<Long> valueIds, String text) {
    }

    /**
     * @param valueIds 옵션 항목마다 고른 값 하나씩이다
     * @param skuCode 없으면 null
     */
    public record OptionInput(
        List<Long> valueIds,
        @PositiveOrZero(message = "초기 재고는 0 이상이어야 합니다.")
        int initialQuantity,
        @Size(max = 255, message = "SKU 코드는 255자 이하로 입력해 주세요.")
        String skuCode) {
    }

    /**
     * @param valueIds 이 묶음의 가격 구분 항목 값
     */
    public record PriceInput(
        List<Long> valueIds,
        @NotNull(message = "판매가를 입력해 주세요.")
        @Positive(message = "판매가는 0보다 커야 합니다.")
        Long price) {
    }

    /**
     * @param measurements 키는 실측 항목 코드이고 값은 cm이다
     */
    public record SizeSpecInput(
        @NotNull(message = "사이즈 값을 골라 주세요.")
        Long sizeValueId,
        @Positive(message = "권장 키는 0보다 커야 합니다.")
        Integer minHeightCm,
        @Positive(message = "권장 키는 0보다 커야 합니다.")
        Integer maxHeightCm,
        Map<String,
            @NotNull(message = "실측 값을 입력해 주세요.")
            @Positive(message = "실측 값은 0보다 커야 합니다.")
            BigDecimal> measurements) {
    }
}
