package com.bbcc.kidly.boundedcontext.review.app;

/**
 * 리뷰 작성자 표시값. 탈퇴한 작성자는 닉네임 대신 "탈퇴한 회원"으로 보인다(저장된 닉네임은 탈퇴 때 지운다).
 */
public final class AuthorDisplay {

    static final String WITHDRAWN_NICKNAME = "탈퇴한 회원";

    private AuthorDisplay() {
    }

    static String nickname(String storedNickname, boolean authorWithdrawn) {
        return authorWithdrawn ? WITHDRAWN_NICKNAME : storedNickname;
    }
}
