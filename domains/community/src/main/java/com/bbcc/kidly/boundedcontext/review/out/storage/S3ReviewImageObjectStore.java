package com.bbcc.kidly.boundedcontext.review.out.storage;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.Deletion;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.ListUnavailableException;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.StoredObject;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.StoredPage;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3 DeleteObject · ListObjectsV2(이름 s3). 지우기는 204와 404(이미 없음)를 지운 것으로 보고, 그 밖(403 · 5xx · 시간 초과 · 연결 실패)은
 * 실패(예외 종류 · 상태 코드)다. 시간 제한은 업로드 확인과 같은 S3 클라이언트의 API 호출 시간 제한이다.
 * 로그 · 실패 종류에 key · 접두어를 넣지 않는다.
 */
@Slf4j
public class S3ReviewImageObjectStore implements ReviewImageObjectStore {

    private static final int NOT_FOUND = 404;

    private final S3Client client;
    private final String bucket;

    public S3ReviewImageObjectStore(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public String name() {
        return "s3";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Deletion delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            return Deletion.deleted();
        } catch (S3Exception e) {
            if (e.statusCode() == NOT_FOUND) {
                return Deletion.deleted();
            }
            return Deletion.failed(failureType(e));
        } catch (SdkException | IllegalStateException e) {
            return Deletion.failed(failureType(e));
        }
    }

    @Override
    public StoredPage list(String prefix, String continuationToken, int pageSize) {
        try {
            ListObjectsV2Response response = client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .maxKeys(pageSize)
                .continuationToken(continuationToken)
                .build());
            return new StoredPage(
                response.contents().stream().map(object -> new StoredObject(object.key(), object.lastModified()))
                    .toList(),
                Boolean.TRUE.equals(response.isTruncated()) ? response.nextContinuationToken() : null);
        } catch (SdkException | IllegalStateException e) {
            String failureType = failureType(e);
            log.warn("리뷰 사진 목록을 읽지 못했습니다. error={}", failureType);
            throw new ListUnavailableException(failureType);
        }
    }

    // 예외 종류(S3 응답이면 상태 코드도). 예외 메시지에는 key가 들어갈 수 있어 쓰지 않는다
    private static String failureType(RuntimeException e) {
        if (e instanceof S3Exception s3) {
            return e.getClass().getSimpleName() + ":" + s3.statusCode();
        }
        return e.getClass().getSimpleName();
    }
}
