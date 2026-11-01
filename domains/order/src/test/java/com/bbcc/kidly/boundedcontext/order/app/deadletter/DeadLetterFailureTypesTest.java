package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.FailureType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

class DeadLetterFailureTypesTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        // 읽기 실패: 본문이 계약과 다름(모르는 enum · 깨진 JSON), eventType 헤더 없음
        "tools.jackson.databind.exc.InvalidFormatException, READ_FAILED",
        "tools.jackson.core.exc.StreamReadException, READ_FAILED",
        "com.bbcc.kidly.boundedcontext.order.app.deadletter.UnreadableOrderMessageException, READ_FAILED",
        // 보관 실패: DB · 트랜잭션(결제 완료 동시 처리에서 진 쪽의 낙관적 락 포함)
        "org.springframework.dao.DataIntegrityViolationException, STORE_FAILED",
        "org.springframework.orm.ObjectOptimisticLockingFailureException, STORE_FAILED",
        "org.springframework.transaction.CannotCreateTransactionException, STORE_FAILED",
        "java.sql.SQLException, STORE_FAILED",
        "jakarta.persistence.PersistenceException, STORE_FAILED",
        // 그 밖: 업무 규칙 · 상태 · null · 모르는 클래스
        "com.bbcc.kidly.global.error.BusinessException, PROCESS_FAILED",
        "java.lang.IllegalStateException, PROCESS_FAILED",
        "java.lang.IllegalArgumentException, PROCESS_FAILED",
        "java.lang.NullPointerException, PROCESS_FAILED",
        "com.example.NoSuchException, PROCESS_FAILED"
    })
    void classifiesByExceptionClass(String exceptionClass, FailureType expected) {
        assertThat(DeadLetterFailureTypes.of(exceptionClass)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    void unknownWithoutExceptionClass(String exceptionClass) {
        assertThat(DeadLetterFailureTypes.of(exceptionClass)).isEqualTo(FailureType.PROCESS_FAILED);
    }
}
