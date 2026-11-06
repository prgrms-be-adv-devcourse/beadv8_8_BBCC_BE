package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.ProductSoldOutInbox;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductSoldOutInboxTest {

    @Test
    void oneOption() {
        var inbox = ProductSoldOutInbox.compose("티셔츠", List.of("110"));
        assertThat(inbox.title()).isEqualTo("옵션이 품절됐어요");
        assertThat(inbox.body()).isEqualTo("티셔츠 상품의 110 옵션이 품절됐어요. 재고를 채우면 다시 팔 수 있어요.");
    }

    @Test
    void multipleOptions() {
        var inbox = ProductSoldOutInbox.compose("티셔츠", List.of("110", "120", "130"));
        assertThat(inbox.body()).isEqualTo("티셔츠 상품의 110 외 2개 옵션이 품절됐어요. 재고를 채우면 다시 팔 수 있어요.");
    }
}
