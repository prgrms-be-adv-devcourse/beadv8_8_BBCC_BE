package com.bbcc.kidly.shared.seller.domain;

// 판매자 상태. 정지 해제는 없다. CLOSED는 회원 탈퇴로 판매자가 닫힌 상태이고 다른 상태로 바뀌지 않는다
public enum SellerStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED
}
