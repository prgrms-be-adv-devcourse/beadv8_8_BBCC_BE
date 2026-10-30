package com.bbcc.kidly.support;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 테스트마다 상품 · 판매자 색인을 비운다(kidly-app의 DatabaseCleanerExtension과 같은 역할). 색인을 지우고 다시 만든다.
 */
class SearchIndexCleanerExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        SpringExtension.getApplicationContext(context).getBean(SearchIndexes.class).reset();
    }
}
