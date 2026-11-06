package com.bbcc.kidly.boundedcontext.review.out.storage;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageUpload;
import com.bbcc.kidly.boundedcontext.review.out.config.CommunityReviewImageStorageProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * S3에 리뷰 사진을 올리는 서명 주소(Presigned PUT)를 만든다. Content-Type · Content-Length를 서명에 넣어
 * 요청한 형식 · 크기로만 올라가게 하고, 이름은 서버가 UUID로 만든다. 서명만 하므로 AWS를 부르지 않는다.
 */
public class S3ReviewImageStorage implements ReviewImageStorage {

    private final S3Presigner presigner;
    private final CommunityReviewImageStorageProperties properties;
    private final ReviewImageUploadChecker uploadChecker;

    public S3ReviewImageStorage(S3Presigner presigner, CommunityReviewImageStorageProperties properties,
        ReviewImageUploadChecker uploadChecker) {
        this.presigner = presigner;
        this.properties = properties;
        this.uploadChecker = uploadChecker;
    }

    @Override
    public UploadUrl issueUploadUrl(ReviewImageUpload upload, long memberId) {
        String key = upload.key(properties.keyPrefix(), memberId, UUID.randomUUID());
        PutObjectRequest putObject = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .contentType(upload.format().contentType())
            .contentLength(upload.contentLength())
            .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(request -> request
            .signatureDuration(properties.uploadUrlExpiry())
            .putObjectRequest(putObject));
        return new UploadUrl(presigned.url().toString(), publicUrl(key), presigned.expiration());
    }

    @Override
    public String ownImageUrlPrefix(long memberId) {
        return publicUrl(ReviewImageUpload.directory(properties.keyPrefix(), memberId));
    }

    /**
     * 공개 주소를 key로 바꿔 한 장씩 확인한다. 우리 버킷 공개 주소가 아니면 없음으로 본다(본인 경로 검사가 먼저라 보통 오지 않는다).
     * 확인 불가가 하나라도 있으면 CheckUnavailableException.
     */
    @Override
    public Set<String> findNotUploaded(List<String> imageUrls) {
        String base = publicUrl("");
        Set<String> missing = new LinkedHashSet<>();
        for (String url : imageUrls) {
            if (!url.startsWith(base)) {
                missing.add(url);
                continue;
            }
            switch (uploadChecker.check(url, url.substring(base.length()))) {
                case PRESENT -> {
                }
                case ABSENT -> missing.add(url);
                case UNKNOWN -> throw new CheckUnavailableException();
            }
        }
        return missing;
    }

    // 버킷의 리뷰 사진 경로는 공개 읽기라 서명 없는 주소로 화면에 보여 준다(버킷 정책에 리뷰 경로가 있어야 한다)
    private String publicUrl(String key) {
        return publicBaseUrl(properties.bucket(), properties.region()) + key;
    }

    /**
     * 버킷 공개 주소(뒤에 key를 붙이면 사진 공개 주소). 정리용 저장소도 같은 규칙으로 주소 ↔ key를 바꾼다.
     */
    public static String publicBaseUrl(String bucket, String region) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/";
    }
}
