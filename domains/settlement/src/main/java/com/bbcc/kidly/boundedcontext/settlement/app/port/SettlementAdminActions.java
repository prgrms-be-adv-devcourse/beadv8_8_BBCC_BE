package com.bbcc.kidly.boundedcontext.settlement.app.port;

import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementAdminActionType;
import java.time.LocalDateTime;

/**
 * 정산 관리자 행위 감사 기록. 추가만 한다. 호출자의 트랜잭션이 있으면 그 안에서 쓴다.
 */
public interface SettlementAdminActions {

    void record(Long adminId, SettlementAdminActionType action, Long targetId, LocalDateTime now);
}
