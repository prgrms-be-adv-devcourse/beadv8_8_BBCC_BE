package com.bbcc.kidly.boundedcontext.review.app.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 리뷰 사진 정리용 저장소. 이 환경의 리뷰 사진 경로만 다룬다: 지우기 · 목록은 정리할 수 있는 key 모양
 * ({이 환경}/reviews/{양수 회원 ID}/{소문자 UUID}.{jpg|png|webp})일 때만 하고, 아니면 저장소를 부르지 않는다. 구현은 out/storage.
 * 결과 · 예외에 key · 주소를 넣지 않는다.
 */
public interface ReviewImageCleanupStorage {

    /**
     * 저장소에 닿을 수 있어 정리를 돌릴 수 있는지. 닿지 않는 설정(로컬의 upload-check none 등)이면 false이고 정리는 돌지 않는다.
     */
    boolean available();

    /**
     * 리뷰에 붙은 공개 주소의 key. 이 환경 리뷰 사진의 공개 주소이고 key가 정리할 수 있는 모양일 때만, 아니면 empty.
     */
    Optional<String> keyOf(String imageUrl);

    /**
     * key의 공개 주소(리뷰에 붙어 있는지 찾을 때 쓴다).
     */
    String imageUrlOf(String key);

    /**
     * 정리할 수 있는 모양의 key인지.
     */
    boolean isCleanable(String key);

    /**
     * 지운다. 없는 key도 지운 것으로 본다(다시 해도 안전). 모양이 아니면 저장소를 부르지 않고 REFUSED.
     */
    Deletion delete(String key);

    /**
     * 이 환경 리뷰 경로를 한 쪽 읽는다.
     *
     * @param continuationToken 직전 쪽의 다음 표지. 처음부터면 null
     * @throws ListUnavailableException 저장소 장애 · 시간 초과 · 권한 문제로 읽지 못했을 때
     */
    StoredPage list(String continuationToken, int pageSize);

    /**
     * @param failureType 실패면 실패 종류(예외 종류 · 상태 코드). 그 밖은 null
     */
    record Deletion(Outcome outcome, String failureType) {

        public static Deletion deleted() {
            return new Deletion(Outcome.DELETED, null);
        }

        public static Deletion refused() {
            return new Deletion(Outcome.REFUSED, null);
        }

        public static Deletion failed(String failureType) {
            return new Deletion(Outcome.FAILED, failureType);
        }

        public enum Outcome {
            DELETED, REFUSED, FAILED
        }
    }

    /**
     * @param lastModified 저장소에 올라간(마지막으로 바뀐) 시각
     */
    record StoredObject(String key, Instant lastModified) {
    }

    /**
     * @param nextContinuationToken 다음 쪽 표지. 마지막 쪽이면 null
     */
    record StoredPage(List<StoredObject> objects, String nextContinuationToken) {
    }

    /**
     * 목록을 읽지 못했다. 메시지에 key · 주소를 넣지 않는다.
     */
    class ListUnavailableException extends RuntimeException {

        public ListUnavailableException(String failureType) {
            super("리뷰 사진 목록을 읽지 못했습니다: " + failureType);
        }
    }
}
