package com.bbcc.kidly.boundedcontext.review.out.store;

import com.bbcc.kidly.boundedcontext.review.app.port.ModerationRunLock;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * 검사 실행 잠금(PostgreSQL 세션 advisory lock, 잠금 이름 kidly.community.review.moderation). 동작은 SessionAdvisoryRunLock.
 */
@Component
public class ModerationRunLockStore extends SessionAdvisoryRunLock implements ModerationRunLock {

    private static final String KEY = "kidly.community.review.moderation";

    public ModerationRunLockStore(DataSource dataSource) {
        super(dataSource, KEY, "리뷰 검사");
    }
}
