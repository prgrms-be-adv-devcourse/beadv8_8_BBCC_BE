package com.bbcc.kidly.boundedcontext.review.app.port;

import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageUpload;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 리뷰 사진 저장소. 브라우저가 직접 올리는 업로드 주소를 서명해 주고, 올린 사진의 공개 주소를 만든다. 구현은 out/storage.
 */
public interface ReviewImageStorage {

    /**
     * 서버가 만든 이름으로 본인 경로에 한 장을 올릴 서명 주소를 만든다. 형식 · 크기는 서명에 들어가 그대로만 올라간다.
     */
    UploadUrl issueUploadUrl(ReviewImageUpload upload, long memberId);

    /**
     * 회원의 사진 공개 주소 접두어(https://{버킷 주소}/{환경}/reviews/{회원 ID}/). 리뷰에 붙인 주소가 본인 것인지 이것으로 본다.
     */
    String ownImageUrlPrefix(long memberId);

    /**
     * 사진이 저장소에 실제로 올라가 있는지 한 장씩 확인한다(새로 붙는 주소만 넘긴다).
     *
     * @return 올라가 있지 않은 주소(넘긴 순서). 모두 있으면 빈 집합
     * @throws CheckUnavailableException 저장소 장애 · 시간 초과 · 권한 문제로 확인할 수 없을 때
     */
    Set<String> findNotUploaded(List<String> imageUrls);

    /**
     * @param uploadUrl PUT으로 올릴 서명 주소
     * @param imageUrl 올린 뒤 화면에 보여 줄 공개 주소(리뷰 작성 · 수정의 imageUrls에 그대로 쓴다)
     * @param expiresAt 서명 만료 시각
     */
    record UploadUrl(String uploadUrl, String imageUrl, Instant expiresAt) {
    }

    /**
     * 업로드 여부를 확인할 수 없다(저장소 장애 · 시간 초과 · 권한 문제). 메시지에 주소를 넣지 않는다.
     */
    class CheckUnavailableException extends RuntimeException {

        public CheckUnavailableException() {
            super("리뷰 사진 업로드 여부를 확인할 수 없습니다");
        }
    }
}
