package com.bbcc.kidly.boundedcontext.settlement;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
final class AdminActions {

    private AdminActions() {
    }

    static List<Row> of(JdbcTemplate jdbc, String action, Long targetId) {
        return jdbc.query("""
            SELECT admin_id, action, target_id FROM settlement.settlement_admin_action
            WHERE action = ? AND target_id = ? ORDER BY id
            """, (rs, row) -> new Row(rs.getLong("admin_id"), rs.getString("action"), rs.getLong("target_id")),
            action, targetId);
    }

    record Row(Long adminId, String action, Long targetId) {
    }
}
