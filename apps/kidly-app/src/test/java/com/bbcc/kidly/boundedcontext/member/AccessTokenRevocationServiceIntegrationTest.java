package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.app.AccessTokenRevocationService;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * 무효화 이벤트(member.tokens-revoked)가 커밋 뒤에만 Kafka로 가는 것은 MemberKafkaPublishIntegrationTest가 본다.
 */
@IntegrationTest
class AccessTokenRevocationServiceIntegrationTest {

    @Autowired
    private AccessTokenRevocationService revocationService;

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 예외가 난다")
    void requiresTransaction() {
        assertThatThrownBy(() -> revocationService.revoke(1L, LocalDateTime.now()))
            .isInstanceOf(IllegalTransactionStateException.class);
    }
}
