package com.bbcc.kidly.shared.auth.out;

import java.util.List;

public interface SocialAccountFinder {

    // 연결된 제공자 이름(예: KAKAO). 없으면 빈 목록. 가입 진행 중인 행은 세지 않는다
    List<String> findProviders(Long memberId);
}
