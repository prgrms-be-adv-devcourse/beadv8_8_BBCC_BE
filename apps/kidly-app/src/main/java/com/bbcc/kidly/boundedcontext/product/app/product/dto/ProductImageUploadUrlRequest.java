package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageFormat;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUpload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 판매자가 상품 이미지를 S3에 올릴 업로드 주소를 요청한다. 올릴 파일마다 형식과 크기를 보낸다.
 *
 * @param files 올릴 이미지 파일 목록. 응답은 이 순서대로 돌려준다
 */
public record ProductImageUploadUrlRequest(
    @NotNull(message = "이미지 목록을 넣어 주세요.")
    @Size(min = 1, max = 10, message = "이미지는 한 번에 1장 이상 10장 이하로 요청해 주세요.")
    List<@Valid FileInput> files) {

    /**
     * @param contentType 파일의 Content-Type(예: image/jpeg)
     * @param contentLength 파일 크기(바이트). 값이 없으면 0으로 받아 크기 검사에서 거절한다
     */
    public record FileInput(
        @NotNull(message = "이미지 형식을 넣어 주세요.")
        @Pattern(regexp = ProductImageFormat.CONTENT_TYPE_PATTERN, message = "jpeg, png, webp 이미지만 올릴 수 있습니다.")
        String contentType,
        @Min(value = 1, message = "이미지는 한 장에 1바이트 이상 10MB 이하로 올려 주세요.")
        @Max(value = ProductImageUpload.MAX_CONTENT_LENGTH, message = "이미지는 한 장에 1바이트 이상 10MB 이하로 올려 주세요.")
        long contentLength) {
    }
}
