package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageUpload;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewRuleException;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewTimes;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

/**
 * 리뷰 사진 업로드 주소 발급. 파일을 모두 확인한 뒤(하나라도 틀리면 하나도 발급하지 않는다) 요청 순서대로 발급한다.
 * 오류: 400 INVALID_IMAGE_FILE(형식 · 크기 · 빈 목록) · TOO_MANY_IMAGES(최대 장수 초과).
 */
@Service
@RequiredArgsConstructor
public class ReviewImageUploadService {

    // 응답 시각은 한국 시간(시간대 표시 없음)
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final ReviewImageStorage imageStorage;
    private final ReviewImageProperties properties;

    public List<IssuedUploadUrl> issue(Long memberId, List<FileSpec> files) {
        if (files == null || files.isEmpty()) {
            throw invalidFile();
        }
        if (files.size() > properties.maxCount()) {
            throw new BusinessException(ReviewErrorCode.TOO_MANY_IMAGES,
                ReviewErrorCode.TOO_MANY_IMAGES.detailWith(properties.maxCount()));
        }
        List<ReviewImageUpload> uploads = new ArrayList<>();
        for (FileSpec file : files) {
            uploads.add(upload(file));
        }
        return uploads.stream().map(upload -> {
            ReviewImageStorage.UploadUrl issued = imageStorage.issueUploadUrl(upload, memberId);
            return new IssuedUploadUrl(issued.uploadUrl(), issued.imageUrl(),
                ReviewTimes.toStoredPrecision(LocalDateTime.ofInstant(issued.expiresAt(), KOREA)));
        }).toList();
    }

    private ReviewImageUpload upload(FileSpec file) {
        if (file == null) {
            throw invalidFile();
        }
        try {
            return ReviewImageUpload.of(file.contentType(), file.contentLength(),
                properties.maxFileSize().toBytes());
        } catch (ReviewRuleException e) {
            throw invalidFile();
        }
    }

    private BusinessException invalidFile() {
        return new BusinessException(ReviewErrorCode.INVALID_IMAGE_FILE,
            ReviewErrorCode.INVALID_IMAGE_FILE.detailWith(sizeText(properties.maxFileSize())));
    }

    // 사람이 읽는 크기(10MB · 512KB · 100B). 나누어떨어지는 가장 큰 단위로 쓴다
    private static String sizeText(DataSize size) {
        long bytes = size.toBytes();
        if (bytes % DataSize.ofMegabytes(1).toBytes() == 0) {
            return size.toMegabytes() + "MB";
        }
        if (bytes % DataSize.ofKilobytes(1).toBytes() == 0) {
            return size.toKilobytes() + "KB";
        }
        return bytes + "B";
    }

    /**
     * 올릴 파일 한 장.
     *
     * @param contentType image/jpeg · image/png · image/webp
     * @param contentLength 바이트 수
     */
    public record FileSpec(String contentType, Long contentLength) {
    }

    /**
     * @param expiresAt 서명 만료 시각(한국 시간)
     */
    public record IssuedUploadUrl(String uploadUrl, String imageUrl, LocalDateTime expiresAt) {
    }
}
