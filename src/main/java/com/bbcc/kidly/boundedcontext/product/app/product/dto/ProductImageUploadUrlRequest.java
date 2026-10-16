package com.bbcc.kidly.boundedcontext.product.app.product.dto;

import java.util.List;

/**
 * 판매자가 상품 이미지를 S3에 올릴 업로드 주소를 요청한다. 올릴 파일마다 형식과 크기를 보낸다.
 *
 * @param files 올릴 이미지 파일 목록. 응답은 이 순서대로 돌려준다
 */
public record ProductImageUploadUrlRequest(List<FileInput> files) {

    /**
     * @param contentType 파일의 Content-Type(예: image/jpeg)
     * @param contentLength 파일 크기(바이트)
     */
    public record FileInput(String contentType, long contentLength) {
    }
}
