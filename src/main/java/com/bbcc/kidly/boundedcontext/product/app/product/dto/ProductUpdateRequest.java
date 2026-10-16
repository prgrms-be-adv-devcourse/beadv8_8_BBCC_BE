package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest.AttributeInput;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest.OptionInput;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest.PriceInput;
import com.bbcc.kidly.boundedcontext.product.app.product.dto.ProductRegisterRequest.SizeSpecInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 판매자가 상품을 고칠 때 보내는 값이다. 등록할 때 보내는 값에 읽어 간 version을 더한다.
 *
 * @param version 판매자가 읽어 간 상품의 version. 그 사이 다른 곳에서 고쳤으면 거절한다
 * @param options 판매대기 상품의 옵션을 바꿔 넣을 때만 보낸다. options · prices · sizeSpecs를 모두 null로 보내면
 *     옵션 · 가격 · 실측은 바꾸지 않는다
 */
public record ProductUpdateRequest(
    @NotNull(message = "version이 필요합니다.")
    Long version,
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
    List<@NotNull(message = "이미지 주소를 넣어 주세요.") String> imageUrls,
    @NotNull(message = "정보 항목 목록이 필요합니다.")
    List<@Valid AttributeInput> attributes,
    List<@Valid OptionInput> options,
    List<@Valid PriceInput> prices,
    List<@Valid SizeSpecInput> sizeSpecs) {
}
