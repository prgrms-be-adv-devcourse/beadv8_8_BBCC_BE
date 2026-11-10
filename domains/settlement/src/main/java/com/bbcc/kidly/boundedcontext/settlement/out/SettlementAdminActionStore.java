package com.bbcc.kidly.boundedcontext.settlement.out;

import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementAdminActions;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementAdminActionType;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 추가만 하는 기록이라 엔티티 없이 JDBC로 넣는다.
 */
@Component
@RequiredArgsConstructor
public class SettlementAdminActionStore implements SettlementAdminActions {

    private final JdbcTemplate jdbc;

    @Override
    public void record(Long adminId, SettlementAdminActionType action, Long targetId, LocalDateTime now) {
        jdbc.update("""
            insert into settlement.settlement_admin_action (admin_id, action, target_id, created_at)
            values (?, ?, ?, ?)
            """, adminId, action.name(), targetId, now);
    }
}
