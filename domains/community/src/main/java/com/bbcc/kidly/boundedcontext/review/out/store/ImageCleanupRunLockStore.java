package com.bbcc.kidly.boundedcontext.review.out.store;

import com.bbcc.kidly.boundedcontext.review.app.port.ImageCleanupRunLock;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * 리뷰 사진 정리 실행 잠금(PostgreSQL 세션 advisory lock, 잠금 이름 kidly.community.review.image-cleanup — 검사 실행과 다른 잠금).
 * 동작은 SessionAdvisoryRunLock.
 */
@Component
public class ImageCleanupRunLockStore extends SessionAdvisoryRunLock implements ImageCleanupRunLock {

    private static final String KEY = "kidly.community.review.image-cleanup";

    public ImageCleanupRunLockStore(DataSource dataSource) {
        super(dataSource, KEY, "리뷰 사진 정리");
    }
}
