package com.bbcc.kidly.boundedcontext.review.out.storage;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageKeys;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageUpload;
import java.util.Optional;

/**
 * 리뷰 사진 정리용 저장소. 공개 주소 ↔ key를 바꾸고, 이 환경 리뷰 경로의 정리할 수 있는 key만 고른 저장소(ReviewImageObjectStore)에 넘긴다.
 */
public class S3ReviewImageCleanupStorage implements ReviewImageCleanupStorage {

    private final ReviewImageObjectStore store;
    private final String keyPrefix;
    private final String publicBaseUrl;

    /**
     * @param publicBaseUrl 버킷 공개 주소(https://{버킷 주소}/, 뒤에 key를 붙이면 사진 주소)
     */
    public S3ReviewImageCleanupStorage(ReviewImageObjectStore store, String keyPrefix, String publicBaseUrl) {
        this.store = store;
        this.keyPrefix = keyPrefix;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public boolean available() {
        return store.available();
    }

    @Override
    public Optional<String> keyOf(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(publicBaseUrl)) {
            return Optional.empty();
        }
        String key = imageUrl.substring(publicBaseUrl.length());
        return isCleanable(key) ? Optional.of(key) : Optional.empty();
    }

    @Override
    public String imageUrlOf(String key) {
        return publicBaseUrl + key;
    }

    @Override
    public boolean isCleanable(String key) {
        return ReviewImageKeys.isCleanable(keyPrefix, key);
    }

    @Override
    public Deletion delete(String key) {
        if (!isCleanable(key)) {
            return Deletion.refused();
        }
        return store.delete(key);
    }

    @Override
    public StoredPage list(String continuationToken, int pageSize) {
        return store.list(ReviewImageUpload.reviewsDirectory(keyPrefix), continuationToken, pageSize);
    }
}
