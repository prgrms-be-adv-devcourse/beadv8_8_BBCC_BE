package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 리뷰 컨텍스트 오류. detail은 받는 쪽이 다음에 할 일을 알 수 있게 쓴다(행동이 다르면 코드를 나눈다).
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    DEAD_LETTER_NOT_FOUND(HttpStatus.NOT_FOUND, "DLT 메시지를 찾을 수 없습니다", "DLT 목록을 새로고침해 주세요."),
    INVALID_DEAD_LETTER_STATE(HttpStatus.BAD_REQUEST, "잘못된 DLT 상태입니다", "상태는 OPEN 또는 REPROCESSED입니다."),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "잘못된 페이지 크기입니다", "size는 1 이상 50 이하 정수로 보내 주세요."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "잘못된 커서입니다",
        "cursor는 직전 응답의 nextCursor를 그대로 보내 주세요. 처음부터 읽으려면 cursor를 빼고 보내 주세요."),
    INVALID_RATING(HttpStatus.BAD_REQUEST, "잘못된 별점입니다", "별점은 1 이상 5 이하 정수로 보내 주세요."),
    INVALID_CONTENT(HttpStatus.BAD_REQUEST, "잘못된 리뷰 본문입니다", "본문은 공백을 빼고 1자 이상 1000자 이하로 써 주세요."),
    INVALID_PRODUCT_ID(HttpStatus.BAD_REQUEST, "잘못된 상품 ID입니다", "productId는 상품의 ID(양의 정수)로 보내 주세요."),
    INVALID_ORDER_ITEM_ID(HttpStatus.BAD_REQUEST, "잘못된 주문상품 ID입니다",
        "orderItemId는 리뷰 작성 가능 목록의 orderItemId를 그대로 보내 주세요."),
    INVALID_FIT(HttpStatus.BAD_REQUEST, "잘못된 사이즈 평가입니다", "fit은 SMALL · TRUE_TO_SIZE · LARGE 중 하나로 보내 주세요."),
    FIT_NOT_APPLICABLE(HttpStatus.BAD_REQUEST, "사이즈 평가를 남길 수 없는 상품입니다",
        "사이즈가 없는 상품에는 사이즈 평가를 남길 수 없어요. fit을 빼고 보내 주세요"),
    // detail의 %s는 한 장 최대 크기(설정 값, 예: 10MB)로 채운다(INVALID_IMAGE_FILE.detailWith(최대 크기))
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "올릴 수 없는 사진 파일입니다",
        "사진은 jpg · png · webp 파일 1장당 %s 이하로, files에 1장 이상 담아 보내 주세요."),
    // detail의 %d는 최대 장수(설정 값)로 채운다(TOO_MANY_IMAGES.detailWith(최대 장수))
    TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST, "사진이 너무 많습니다", "사진은 최대 %d장까지 올릴 수 있어요. 사진 수를 줄여 보내 주세요."),
    INVALID_IMAGE_URL(HttpStatus.BAD_REQUEST, "쓸 수 없는 사진 주소입니다",
        "사진은 업로드 주소 발급(/api/v1/reviews/images/upload-urls)으로 받은 imageUrl만 쓸 수 있어요. 받은 주소를 그대로 문자열 목록으로 보내 주세요."),
    INVALID_MODERATION_STATUS(HttpStatus.BAD_REQUEST, "잘못된 검사 상태입니다",
        "status는 PENDING · PASSED · HIDDEN · CONFIRMED_HIDDEN · RESTORED 중 하나로 보내 주세요."),
    INVALID_VISIBILITY(HttpStatus.BAD_REQUEST, "잘못된 공개 설정입니다",
        "visibility는 HIDDEN(숨김 확정) 또는 VISIBLE(다시 공개)로 보내 주세요."),
    NO_FIELDS_TO_UPDATE(HttpStatus.BAD_REQUEST, "바꿀 값이 없습니다", "바꿀 값을 하나 이상 보내 주세요."),
    REVIEW_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "리뷰를 쓸 수 없는 주문상품입니다",
        "구매확정한 내 주문상품에만 리뷰를 쓸 수 있습니다. 리뷰 작성 가능 목록에서 골라 주세요."),
    REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "내 리뷰가 아닙니다", "내가 쓴 리뷰만 고치거나 지울 수 있습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다", "없거나 지워진 리뷰입니다. 리뷰 목록을 새로고침해 주세요."),
    REVIEW_ALREADY_WRITTEN(HttpStatus.CONFLICT, "이미 리뷰를 쓴 주문상품입니다",
        "주문상품마다 리뷰는 하나입니다. 내 리뷰를 고치려면 수정해 주세요.");

    private final HttpStatus status;
    private final String title;
    private final String detail;

    /**
     * detail에 값을 채운 응답 문구(TOO_MANY_IMAGES의 최대 장수 · INVALID_IMAGE_FILE의 최대 크기).
     */
    public String detailWith(Object... values) {
        return detail.formatted(values);
    }
}
