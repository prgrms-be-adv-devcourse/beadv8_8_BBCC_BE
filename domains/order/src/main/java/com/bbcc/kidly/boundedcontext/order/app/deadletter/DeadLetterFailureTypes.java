package com.bbcc.kidly.boundedcontext.order.app.deadletter;

import com.bbcc.kidly.boundedcontext.order.app.port.OrderDeadLetterRepository.FailureType;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import tools.jackson.core.JacksonException;

/**
 * DLT 헤더의 예외 종류(이름)로 실패 유형을 정한다(#601). 주문 Kafka 에러 처리기(#541 · #594) 기준:
 * <ul>
 *   <li>읽기 실패: 본문을 읽지 못함(Jackson — 모르는 enum 값 · 깨진 JSON) · eventType 헤더 없음. 재시도 없이 온다</li>
 *   <li>보관 실패: DB · 트랜잭션 오류. 재고 · 결제 처리가 주문 자체 보관까지 실패해 다시 던졌거나, 상품 복사본 저장이 실패했다.
 *       결제 완료의 동시 처리에서 진 쪽이 끝까지 결제대기(낙관적 락)인데 재시도 뒤 보관(#632)까지 실패한 것도 여기다</li>
 *   <li>그 밖: 업무 규칙 · 상태 · null 등 다시 해도 같은 예외, 이 앱에 없는 예외 종류</li>
 * </ul>
 */
final class DeadLetterFailureTypes {

    private static final List<Class<?>> READ = List.of(JacksonException.class, UnreadableOrderMessageException.class);
    private static final List<Class<?>> STORE = List.of(DataAccessException.class, TransactionException.class,
        PersistenceException.class, SQLException.class);

    private DeadLetterFailureTypes() {
    }

    static FailureType of(String exceptionClass) {
        Class<?> type = load(exceptionClass);
        if (type == null) {
            return FailureType.PROCESS_FAILED;
        }
        if (isAny(READ, type)) {
            return FailureType.READ_FAILED;
        }
        if (isAny(STORE, type)) {
            return FailureType.STORE_FAILED;
        }
        return FailureType.PROCESS_FAILED;
    }

    private static boolean isAny(List<Class<?>> types, Class<?> type) {
        return types.stream().anyMatch(candidate -> candidate.isAssignableFrom(type));
    }

    // 초기화하지 않고 찾기만 한다. 없는 종류(다른 버전 · 다른 앱의 예외)는 null
    private static Class<?> load(String exceptionClass) {
        if (exceptionClass == null) {
            return null;
        }
        try {
            return Class.forName(exceptionClass, false, DeadLetterFailureTypes.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
