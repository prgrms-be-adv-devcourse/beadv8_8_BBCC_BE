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

    // 잠금 키 접두어. 다른 advisory lock은 'kidly.{도메인}.{작업}' 문자열의 해시를 키로 쓴다(주문 · 정산 · 결제 릴레이 등).
    // 알림 전용 접두어에 회원 ID를 붙여 64비트로 해시하므로 그 키들과 겹치지 않는다. 회원끼리 해시가 겹치면 잠깐 더 기다릴 뿐이다
    private static final String LOCK_KEY_PREFIX = "kidly.notification.member-withdrawal:";

    private final JdbcTemplate jdbc;

    @Override
    public void markIfAbsent(Long memberId, LocalDateTime withdrawnAt) {
        jdbc.update("""
            insert into notification.withdrawn_member (member_id, withdrawn_at) values (?, ?)
            on conflict (member_id) do nothing
            """, memberId, withdrawnAt);
    }

    // 트랜잭션 잠금이다. 트랜잭션이 끝나면 연결을 풀에 돌려주는 설정이라 세션 잠금은 같은 연결로 풀 수 없다
    @Override
    public void lock(Long memberId) {
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))::text", String.class,
            LOCK_KEY_PREFIX + memberId);
    }

    @Override
    public boolean exists(Long memberId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "select exists(select 1 from notification.withdrawn_member where member_id = ?)", Boolean.class,
            memberId));
    }
}
