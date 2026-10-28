package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 추천 결과에 붙는 안내다. message는 화면에 그대로 보여 주는 문구이고, 체형 판정값은 문구에 쓰지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum SizeNotice {

    SIZE_FREE("사이즈 구분이 없는 상품이에요."),
    NO_MATCHING_SIZE("아이 키에 맞는 사이즈가 없어요."),
    BODY_TYPE_ADJUSTED("입력하신 체형보다 키와 몸무게를 우선해 추천했어요."),
    PREFERRED_FIT_NOT_APPLIED("아이 체형을 먼저 고려해 선호 핏과 다른 사이즈를 추천했어요."),
    HEIGHT_ONLY("입력하신 체형이 키·몸무게와 많이 달라 키만 기준으로 추천했어요.");

    private final String message;
}
