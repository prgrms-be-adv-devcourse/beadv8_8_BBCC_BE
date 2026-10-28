package com.bbcc.kidly.boundedcontext.product.in.product.dto;

import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageUploadUrl;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 상품 이미지 업로드 주소 발급 응답이다. 요청한 파일 순서대로 담는다.
 *
 * @param uploads 파일마다 발급한 업로드 주소
 */
public record ProductImageUploadUrlsResponse(List<Upload> uploads) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * @param uploadUrl 이미지를 PUT으로 올릴 서명된 주소
     * @param imageUrl 올린 뒤 화면에 보여 줄 이미지 주소
     * @param expiresAt 업로드 주소가 만료되는 시각(서울 시간)
     */
    public record Upload(String uploadUrl, String imageUrl, LocalDateTime expiresAt) {
    }

    public static ProductImageUploadUrlsResponse from(List<ProductImageUploadUrl> issued) {
        List<Upload> uploads = new ArrayList<>();
        for (ProductImageUploadUrl url : issued) {
            uploads.add(new Upload(
                url.uploadUrl(), url.imageUrl(), LocalDateTime.ofInstant(url.expiresAt(), SEOUL)));
        }
        return new ProductImageUploadUrlsResponse(uploads);
    }
}
