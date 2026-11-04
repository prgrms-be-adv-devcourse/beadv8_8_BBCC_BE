package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccountRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ChildAccountRepositoryIntegrationTest {
    @Autowired
    private ChildAccountRepository accounts;
    @Autowired
    private TransactionTemplate transactions;

    @Test
    void checksPasswordHashInDatabaseDespiteCachedAccount() {
        var id = transactions.execute(status -> accounts.saveAccount(
            ChildAccount.create("child-cache", "old-hash", 1L, 2L)).getId());
        try (var executor = Executors.newSingleThreadExecutor()) {
            transactions.executeWithoutResult(status -> {
                var cached = accounts.findByLoginId("child-cache").orElseThrow();
                assertThat(cached.getPasswordHash()).isEqualTo("old-hash");
                try {
                    executor.submit(() -> transactions.executeWithoutResult(inner ->
                        accounts.findByIdForUpdate(id).orElseThrow().changePassword("new-hash"))).get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                assertThat(accounts.findByIdAndPasswordHashForUpdate(id, "old-hash")).isEmpty();
                assertThat(accounts.findByIdAndPasswordHashForUpdate(id, "new-hash")).isPresent();
            });
        }
    }
}
