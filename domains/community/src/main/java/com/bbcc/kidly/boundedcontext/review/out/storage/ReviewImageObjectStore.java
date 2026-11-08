package com.bbcc.kidly.boundedcontext.review.out.storage;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.Deletion;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.StoredPage;

/**
 * 리뷰 사진 객체 지우기 · 목록(정리용). 업로드 확인과 같은 이름(설정 kidly.community.review.image.upload-check)으로 고른다:
 * s3(DeleteObject · ListObjectsV2) · none(저장소에 닿지 않음, 정리하지 않음). key 모양 검사는 부르는 쪽(S3ReviewImageCleanupStorage)이
 * 먼저 한다. 지우기는 예외를 던지지 않는다(실패는 FAILED와 실패 종류). 로그 · 실패 종류에 key를 넣지 않는다.
 */
public interface ReviewImageObjectStore {

    String name();

    /**
     * 저장소에 닿을 수 있는지(none이면 false).
     */
    boolean available();

    Deletion delete(String key);

    /**
     * @param prefix 이 접두어로 시작하는 key만 읽는다
     * @throws com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.ListUnavailableException 읽지 못했을 때
     */
    StoredPage list(String prefix, String continuationToken, int pageSize);
}
