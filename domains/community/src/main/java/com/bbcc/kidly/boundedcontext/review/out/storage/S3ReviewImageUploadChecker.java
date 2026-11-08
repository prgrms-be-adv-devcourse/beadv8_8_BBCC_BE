package com.bbcc.kidly.boundedcontext.review.out.storage;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3 HeadObject로 사진이 있는지 확인한다(이름 s3). 200 있음, 404 없음, 그 밖(403 · 5xx · 시간 초과 · 연결 실패) 확인 불가.
 * 403은 권한 문제라 사용자 잘못(없음)으로 보지 않는다(ListBucket 권한이 없으면 없는 객체에도 403이 온다).
 * 시간 제한은 S3 클라이언트의 API 호출 시간 제한(upload-check-timeout)이다. 로그에 주소 · key를 남기지 않는다(상태 코드 · 예외 종류만).
 */
@Slf4j
public class S3ReviewImageUploadChecker implements ReviewImageUploadChecker {

    private static final int NOT_FOUND = 404;

    private final S3Client client;
    private final String bucket;

    public S3ReviewImageUploadChecker(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public String name() {
        return "s3";
    }

    @Override
    public Presence check(String imageUrl, String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return Presence.PRESENT;
        } catch (S3Exception e) {
            if (e.statusCode() == NOT_FOUND) {
                return Presence.ABSENT;
            }
            log.warn("리뷰 사진 업로드 확인을 하지 못했습니다. status={} error={}", e.statusCode(),
                e.getClass().getSimpleName());
            return Presence.UNKNOWN;
        } catch (SdkException | IllegalStateException e) {
            log.warn("리뷰 사진 업로드 확인을 하지 못했습니다. error={}", e.getClass().getSimpleName());
            return Presence.UNKNOWN;
        }
    }
}
