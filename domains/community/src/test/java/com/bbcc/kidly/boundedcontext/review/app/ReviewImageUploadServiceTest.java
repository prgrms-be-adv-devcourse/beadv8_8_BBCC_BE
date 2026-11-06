package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.ReviewImageUploadService.FileSpec;
import com.bbcc.kidly.boundedcontext.review.app.ReviewImageUploadService.IssuedUploadUrl;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageUpload;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class ReviewImageUploadServiceTest {

    private static final long ME = 13L;
    // 2026-11-06 01:02:03.123456789 UTC = 한국 시간 10:02:03.123456789
    private static final Instant EXPIRES = Instant.parse("2026-11-06T01:02:03.123456789Z");

    private final FakeStorage storage = new FakeStorage();

    @Test
    @DisplayName("파일마다 업로드 주소를 요청 순서대로 발급하고, 만료 시각은 한국 시간 LocalDateTime(마이크로초)이다")
    void issuesInRequestOrder() {
        List<IssuedUploadUrl> issued = service(5, DataSize.ofMegabytes(10)).issue(ME,
            List.of(new FileSpec("image/jpeg", 100L), new FileSpec("image/webp", 200L)));

        assertThat(issued).extracting(IssuedUploadUrl::imageUrl).containsExactly("image-0.jpg", "image-1.webp");
        assertThat(issued).extracting(IssuedUploadUrl::uploadUrl).containsExactly("upload-0", "upload-1");
        assertThat(issued).extracting(IssuedUploadUrl::expiresAt)
            .containsOnly(LocalDateTime.of(2026, 11, 6, 10, 2, 3, 123_456_000));
        assertThat(storage.uploads).extracting(ReviewImageUpload::contentLength).containsExactly(100L, 200L);
        assertThat(storage.memberIds).containsOnly(ME);
    }

    @Test
    @DisplayName("파일이 없거나(빈 목록 · null) 항목이 null이면 INVALID_IMAGE_FILE")
    void noFiles() {
        ReviewImageUploadService service = service(5, DataSize.ofMegabytes(10));

        assertError(() -> service.issue(ME, List.of()), ReviewErrorCode.INVALID_IMAGE_FILE);
        assertError(() -> service.issue(ME, null), ReviewErrorCode.INVALID_IMAGE_FILE);
        assertError(() -> service.issue(ME, Collections.singletonList(null)), ReviewErrorCode.INVALID_IMAGE_FILE);
        assertThat(storage.uploads).isEmpty();
    }

    @Test
    @DisplayName("최대 장수까지 발급하고, 넘으면 TOO_MANY_IMAGES(detail에 최대 장수)이고 하나도 발급하지 않는다")
    void countBoundaries() {
        ReviewImageUploadService service = service(5, DataSize.ofMegabytes(10));

        assertThat(service.issue(ME, files(5))).hasSize(5);
        storage.uploads.clear();
        assertThatThrownBy(() -> service.issue(ME, files(6))).isInstanceOfSatisfying(BusinessException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.TOO_MANY_IMAGES);
            assertThat(e.getDetail()).contains("5장");
        });
        assertThat(storage.uploads).isEmpty();
    }

    @Test
    @DisplayName("형식 · 크기가 틀린 파일이 하나라도 있으면 INVALID_IMAGE_FILE이고 앞 파일도 발급하지 않는다")
    void invalidFileIssuesNothing() {
        ReviewImageUploadService service = service(5, DataSize.ofMegabytes(10));

        assertError(() -> service.issue(ME, List.of(new FileSpec("image/png", 1L), new FileSpec("image/gif", 1L))),
            ReviewErrorCode.INVALID_IMAGE_FILE);
        assertError(() -> service.issue(ME, List.of(new FileSpec("image/png", 1L),
            new FileSpec("image/png", 10_485_761L))), ReviewErrorCode.INVALID_IMAGE_FILE);
        assertError(() -> service.issue(ME, List.of(new FileSpec(null, null))), ReviewErrorCode.INVALID_IMAGE_FILE);
        assertThat(storage.uploads).isEmpty();
        assertThat(service.issue(ME, List.of(new FileSpec("image/png", 10_485_760L)))).hasSize(1);
    }

    @Test
    @DisplayName("설정 값(최대 장수 · 최대 크기)을 바꾸면 코드 수정 없이 그 값으로 검사한다")
    void followsConfiguredLimits() {
        ReviewImageUploadService service = service(2, DataSize.ofBytes(100));

        assertThat(service.issue(ME, files(2))).hasSize(2);
        assertThatThrownBy(() -> service.issue(ME, files(3))).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getDetail()).contains("2장"));
        assertError(() -> service.issue(ME, List.of(new FileSpec("image/png", 101L))),
            ReviewErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    @DisplayName("INVALID_IMAGE_FILE detail의 최대 크기는 설정 값(max-file-size)으로 채운다")
    void fileErrorShowsConfiguredSize() {
        assertThat(fileErrorDetail(DataSize.ofMegabytes(10))).contains("10MB").doesNotContain("%");
        assertThat(fileErrorDetail(DataSize.ofMegabytes(20))).contains("20MB").doesNotContain("10MB");
        assertThat(fileErrorDetail(DataSize.ofKilobytes(512))).contains("512KB");
        assertThat(fileErrorDetail(DataSize.ofBytes(100))).contains("100B");
    }

    private String fileErrorDetail(DataSize maxFileSize) {
        ReviewImageUploadService service = service(5, maxFileSize);
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
            () -> service.issue(ME, List.of(new FileSpec("image/gif", 1L))));
        assertThat(thrown).isInstanceOf(BusinessException.class);
        return ((BusinessException) thrown).getDetail();
    }

    private ReviewImageUploadService service(int maxCount, DataSize maxFileSize) {
        return new ReviewImageUploadService(storage, new ReviewImageProperties(maxCount, maxFileSize));
    }

    private static List<FileSpec> files(int count) {
        List<FileSpec> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            files.add(new FileSpec("image/png", 10L));
        }
        return files;
    }

    private static void assertError(Runnable call, ReviewErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private static final class FakeStorage implements ReviewImageStorage {

        private final List<ReviewImageUpload> uploads = new ArrayList<>();
        private final List<Long> memberIds = new ArrayList<>();

        @Override
        public UploadUrl issueUploadUrl(ReviewImageUpload upload, long memberId) {
            int index = uploads.size();
            uploads.add(upload);
            memberIds.add(memberId);
            return new UploadUrl("upload-" + index, "image-" + index + "." + upload.format().extension(), EXPIRES);
        }

        @Override
        public String ownImageUrlPrefix(long memberId) {
            throw new UnsupportedOperationException();
        }
    }
}
