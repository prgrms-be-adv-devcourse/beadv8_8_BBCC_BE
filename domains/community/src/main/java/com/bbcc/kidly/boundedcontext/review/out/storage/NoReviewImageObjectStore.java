package com.bbcc.kidly.boundedcontext.review.out.storage;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.Deletion;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.StoredPage;
import java.util.List;

/**
 * 저장소에 닿지 않는 설정(이름 none, 로컬 실행). 정리는 돌지 않고, 불려도 아무것도 지우지 않는다.
 */
public class NoReviewImageObjectStore implements ReviewImageObjectStore {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public Deletion delete(String key) {
        return Deletion.refused();
    }

    @Override
    public StoredPage list(String prefix, String continuationToken, int pageSize) {
        return new StoredPage(List.of(), null);
    }
}
