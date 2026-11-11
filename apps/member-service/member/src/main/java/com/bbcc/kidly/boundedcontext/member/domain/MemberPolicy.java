package com.bbcc.kidly.boundedcontext.member.domain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 회원 도메인의 Kidly 정책 값. 요청 DTO의 Bean Validation과 컬럼 길이가 함께 쓴다.
 * 닉네임 규칙은 {@link Nickname}, 닉네임 중복은 {@link NicknamePolicy}, 기본 배송지는 {@link DefaultShippingAddressPolicy}.
 */
public final class MemberPolicy {

    public static final int SUSPEND_REASON_MAX_LENGTH = 200;

    // 배송지
    public static final int RECIPIENT_NAME_MAX_LENGTH = 50;
    public static final String RECIPIENT_PHONE_REGEX = "0[0-9]{1,2}-[0-9]{3,4}-[0-9]{4}";
    public static final int RECIPIENT_PHONE_MAX_LENGTH = 13;
    public static final int ZIP_CODE_MAX_LENGTH = 10;
    public static final int ROAD_ADDRESS_MAX_LENGTH = 200;
    public static final int DETAIL_ADDRESS_MAX_LENGTH = 100;

    // 판매자 탈퇴를 막는 이유의 이름. 표에 없는 이유도 막으므로 일반 이름으로 적는다
    private static final Map<String, String> WITHDRAWAL_BLOCKER_NAMES = Map.of(
        WithdrawalBlocker.UNCONFIRMED_ORDER_ITEM, "구매확정 전 주문상품",
        "UNSETTLED_ITEM", "정산 전 항목",
        "UNPAID_SETTLEMENT", "지급되지 않은 정산");
    private static final String UNKNOWN_WITHDRAWAL_BLOCKER_NAME = "확인이 필요한 항목";

    private MemberPolicy() {
    }

    public static String withdrawalBlockedDetail(List<WithdrawalBlocker> blockers) {
        var reasons = blockers.stream()
            .map(blocker -> withdrawalBlockerName(blocker.type()) + " " + blocker.count() + "건")
            .collect(Collectors.joining(", "));
        return reasons + "이 남아 있어 탈퇴할 수 없어요.";
    }

    private static String withdrawalBlockerName(String type) {
        return WITHDRAWAL_BLOCKER_NAMES.getOrDefault(type, UNKNOWN_WITHDRAWAL_BLOCKER_NAME);
    }
}
