package com.bbcc.kidly.boundedcontext.notification.out;

import com.bbcc.kidly.boundedcontext.notification.domain.WithdrawnMemberRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회원 ID와 시각 두 칸뿐이라 엔티티 없이 JDBC로 쓴다. 같은 탈퇴를 동시에 받아도 ON CONFLICT DO NOTHING으로 한 행이다.
 */
@Repository
@RequiredArgsConstructor
public class JdbcWithdrawnMemberRepository implements WithdrawnMemberRepository {

    private final JdbcTemplate jdbc;

    @Override
    public void markIfAbsent(Long memberId, LocalDateTime withdrawnAt) {
        jdbc.update("""
            insert into notification.withdrawn_member (member_id, withdrawn_at) values (?, ?)
            on conflict (member_id) do nothing
            """, memberId, withdrawnAt);
    }

    @Override
    public boolean exists(Long memberId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "select exists(select 1 from notification.withdrawn_member where member_id = ?)", Boolean.class,
            memberId));
    }
}
