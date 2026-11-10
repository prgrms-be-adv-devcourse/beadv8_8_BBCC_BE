package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.app.ChildProfileReplicaService;
import com.bbcc.kidly.boundedcontext.auth.domain.ChildProfileReplicaRepository;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ChildProfileReplicaServiceIntegrationTest {

    private static final Long CHILD_ID = 10L;
    private static final Long GUARDIAN_ID = 1L;

    @Autowired
    private ChildProfileReplicaService service;

    @Autowired
    private ChildProfileReplicaRepository replicas;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    @DisplayName("삭제가 반영된 뒤 늦게 온 변경 이벤트는 복사본을 되살리지 않는다")
    void staleChangeAfterDelete() {
        service.apply(CHILD_ID, GUARDIAN_ID, false);
        service.apply(CHILD_ID, GUARDIAN_ID, true);

        service.apply(CHILD_ID, GUARDIAN_ID, false);

        assertThat(replicaExists()).isFalse();
    }

    @Test
    @DisplayName("삭제가 생성보다 먼저 와도 뒤에 온 생성은 복사본을 만들지 않는다")
    void deleteBeforeCreate() {
        service.apply(CHILD_ID, GUARDIAN_ID, true);

        service.apply(CHILD_ID, GUARDIAN_ID, false);

        assertThat(replicaExists()).isFalse();
    }

    private boolean replicaExists() {
        return Boolean.TRUE.equals(transactions.execute(status -> replicas.findByIdForUpdate(CHILD_ID).isPresent()));
    }
}
