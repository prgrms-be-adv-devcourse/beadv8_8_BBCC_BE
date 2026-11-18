package com.bbcc.kidly.boundedcontext.notification.app;

import java.util.List;

public record ProductSoldOutInbox(String title, String body) {

    public static ProductSoldOutInbox compose(String productName, List<String> optionNames) {
        var option = optionNames.size() == 1 ? optionNames.getFirst()
            : "%s 외 %d개".formatted(optionNames.getFirst(), optionNames.size() - 1);
        return new ProductSoldOutInbox("옵션이 품절됐어요",
            "%s 상품의 %s 옵션이 품절됐어요. 재고를 채우면 다시 팔 수 있어요.".formatted(productName, option));
    }
}
