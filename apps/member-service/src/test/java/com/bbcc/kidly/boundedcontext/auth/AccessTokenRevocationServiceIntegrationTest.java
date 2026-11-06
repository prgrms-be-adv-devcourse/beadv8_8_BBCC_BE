package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.auth.app.AccessTokenRevocationService;
import com.bbcc.kidly.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

/**
 * 무효화 이벤트(auth.tokens-revoked)가 커밋 뒤에만 Kafka로 가는 것은 AuthKafkaPublishIntegrationTest가 본다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AccessTokenRevocationServiceIntegrationTest {

    @Autowired
    private AccessTokenRevocationService revocationService;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 예외가 난다")
    void requiresTransaction() {
        assertThatThrownBy(() -> revocationService.revoke(1L, LocalDateTime.now()))
            .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("커밋되면 무효화 로그를 남기고, 롤백되면 남기지 않는다")
    void logsOnlyAfterCommit(CapturedOutput output) {
        transactions.executeWithoutResult(status -> revocationService.revoke(101L, LocalDateTime.now()));
        transactions.executeWithoutResult(status -> {
            revocationService.revoke(202L, LocalDateTime.now());
            status.setRollbackOnly();
        });

        assertThat(output.getOut())
            .contains("Access Token 무효화: type=MEMBER principalId=101")
            .doesNotContain("principalId=202");
    }
}
