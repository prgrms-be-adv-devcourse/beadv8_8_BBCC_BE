package com.bbcc.kidly.boundedcontext.recommend.domain.activity;

/**
 * 찜 해제 원인을 보고 선호 집계가 할 일을 정한다. 원인 값은 공유 계약의 찜 해제 원인 이름이다.
 */
public final class FavoriteRemoval {

    // 회원이 직접 해제했다
    private static final String USER_REMOVAL_CAUSE = "USER";

    // 회원 탈퇴로 지웠다
    private static final String MEMBER_WITHDRAWN_REMOVAL_CAUSE = "MEMBER_WITHDRAWN";

    private FavoriteRemoval() {
    }

    /**
     * 찜 해제가 회원이 직접 거둔 것(USER)인지 알려 준다. 공유 계약에 새 원인이 더해져도 USER가 아니면 거둔 것으로 보지 않는다.
     * 계약을 받기 전에는 읽지 못한 원인 값이 재시도 없이 DLT로 간다.
     */
    public static boolean isWithdrawnByMember(String cause) {
        return USER_REMOVAL_CAUSE.equals(cause);
    }

    /**
     * 찜이 회원 탈퇴로 지워진 것인지 알려 준다. 회원 토픽의 탈퇴와 커뮤니티 토픽의 찜은 순서가 없어,
     * 탈퇴 처리가 활동 기록을 지운 뒤에 찜이 늦게 올 수 있다. 그 찜 기록을 지우는 데 쓴다.
     */
    public static boolean isByWithdrawal(String cause) {
        return MEMBER_WITHDRAWN_REMOVAL_CAUSE.equals(cause);
    }
}
