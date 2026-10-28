package com.bbcc.kidly.shared.product.domain;

// 옵션 판매 상태. 판매 중(ON_SALE), 판매 중지(STOPPED). 보관한 옵션은 이벤트에 넣지 않으므로 보관 값은 없다
public enum OptionSaleStatus {
    ON_SALE,
    STOPPED
}
