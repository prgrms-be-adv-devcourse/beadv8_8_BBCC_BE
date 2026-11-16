package com.bbcc.kidly.shared.community.event;

/**
 * 찜 이벤트의 {@code ownerType} 값. 받는 쪽 옛 버전도 읽을 수 있게 enum이 아닌 문자열로 보낸다.
 * 소유자 칸이 없는 옛 메시지는 회원 본인 찜이다({@code ownerId} = {@code memberId}).
 */
public final class FavoriteOwnerTypes {

    // 회원 본인 찜. ownerId = memberId
    public static final String MEMBER = "MEMBER";
    // 자녀 계정 찜. ownerId = 자녀 계정 ID, memberId = 보호자 회원 ID
    public static final String CHILD = "CHILD";

    private FavoriteOwnerTypes() {
    }
}
