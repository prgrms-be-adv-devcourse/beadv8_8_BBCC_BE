package com.bbcc.kidly.boundedcontext.product.domain.product;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 상품 썸네일과 이미지 목록이 판매자 본인이 업로드 주소로 올린 이미지인지 검사한다.
 * 본인 경로(ownPrefix) 아래에 서버가 만든 이름({UUID}.{확장자})으로 올린 이미지만 받는다.
 */
public final class ProductImageUrls {

    private static final Pattern SERVER_MADE_NAME = serverMadeName();

    private ProductImageUrls() {
    }

    public static void validate(String thumbnailUrl, List<String> imageUrls, String ownPrefix) {
        if (thumbnailUrl == null) {
            return;
        }
        // ownPrefix가 /로 끝나므로 판매자 2의 경로(.../products/2/)는 판매자 23의 경로(.../products/23/)와 구분된다
        if (!thumbnailUrl.startsWith(ownPrefix)
            || !SERVER_MADE_NAME.matcher(thumbnailUrl.substring(ownPrefix.length())).matches()) {
            throw new BusinessException(ProductErrorCode.IMAGE_URL_NOT_ALLOWED);
        }
    }

    // 확장자 목록은 업로드 주소를 만들 때 쓰는 ProductImageFormat에서 가져와 한곳에서만 관리한다
    private static Pattern serverMadeName() {
        StringBuilder extensions = new StringBuilder();
        for (ProductImageFormat format : ProductImageFormat.values()) {
            if (!extensions.isEmpty()) {
                extensions.append('|');
            }
            extensions.append(Pattern.quote(format.extension()));
        }
        return Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(" + extensions + ")");
    }
}
