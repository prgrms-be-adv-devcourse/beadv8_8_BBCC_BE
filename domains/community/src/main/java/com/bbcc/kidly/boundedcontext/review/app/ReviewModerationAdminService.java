package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImages;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerations;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerationStats;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerationStats.StatusCountRow;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerations.AdminReviewRow;
import com.bbcc.kidly.boundedcontext.review.app.port.Reviews;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationStatus;
import com.bbcc.kidly.boundedcontext.review.domain.Review;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImage;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewTimes;
import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 리뷰 검사: 상태별 목록(판단용 본문 · 사진 포함)과 숨김 확정 · 다시 공개.
 * 공개 ↔ 숨김이 바뀌면 같은 트랜잭션에서 그 리뷰 몫을 집계에서 빼거나 더한다. 같은 값을 다시 보내도 결과는 같다.
 */
@Service
@RequiredArgsConstructor
public class ReviewModerationAdminService {

    private final ReviewModerations reviewModerations;
    private final Reviews reviews;
    private final ReviewImages reviewImages;
    private final ReviewStatsShare statsShare;
    private final ReviewModerationProperties properties;
    private final ReviewModerationStats moderationStats;

    /**
     * 검사 현황(운영 조회): 상태별 리뷰 수(대기는 상한 전 PENDING · 상한 도달 PENDING_EXHAUSTED로 나눔, 없는 상태 0),
     * 가장 오래된 대기 리뷰의 작성 시각, 최근 숨긴 리뷰(ID · 이유 · 시각만, recentLimit까지).
     */
    @Transactional(readOnly = true)
    public ModerationOverview overview(int recentLimit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ModerationStatus status : ModerationStatus.values()) {
            counts.put(status.name(), 0L);
        }
        counts.put(PENDING_EXHAUSTED, 0L);
        for (StatusCountRow row : moderationStats.countByModerationStatus()) {
            counts.put(row.getStatus().name(), row.getTotal());
        }
        long exhausted = moderationStats.countExhaustedPending(properties.maxAttempts());
        // 두 조회 사이에 상태가 바뀌어도 음수가 되지 않게 한다
        long pending = counts.get(ModerationStatus.PENDING.name());
        counts.put(ModerationStatus.PENDING.name(), Math.max(0, pending - exhausted));
        counts.put(PENDING_EXHAUSTED, exhausted);
        List<RecentHidden> recent = moderationStats.findRecentlyHidden(Limit.of(recentLimit)).stream()
            .map(row -> new RecentHidden(row.getId(), row.getReason() == null ? null : row.getReason().name(),
                row.getModeratedAt()))
            .toList();
        return new ModerationOverview(counts, moderationStats.findOldestPendingCreatedAt(properties.maxAttempts()),
            recent);
    }

    /**
     * 최신순(id desc). 커서는 마지막 항목의 id다.
     *
     * @param status PENDING · PASSED · HIDDEN · CONFIRMED_HIDDEN · RESTORED. 없거나 다른 값이면 INVALID_MODERATION_STATUS
     */
    @Transactional(readOnly = true)
    public AdminReviewPage list(String status, String cursor, String size) {
        ModerationStatus moderationStatus = parseStatus(status);
        int pageSize = PageRequestValues.pageSize(size);
        Limit limit = Limit.of(pageSize + 1);
        List<AdminReviewRow> rows = cursor == null
            ? reviewModerations.findByModerationStatus(moderationStatus, limit)
            : reviewModerations.findByModerationStatusAfter(moderationStatus,
                PageRequestValues.IdCursor.decode(cursor).id(), limit);
        List<AdminReviewRow> page = rows.stream().limit(pageSize).toList();
        Map<Long, List<String>> images = page.isEmpty() ? Map.of()
            : reviewImages.findByReviewIdInOrderByReviewIdAscPositionAsc(page.stream().map(AdminReviewRow::getId)
                    .toList())
                .stream()
                .collect(Collectors.groupingBy(ReviewImage::getReviewId,
                    Collectors.mapping(ReviewImage::getImageUrl, Collectors.toList())));
        List<AdminReviewItem> items = page.stream()
            .map(row -> AdminReviewItem.from(row, images.getOrDefault(row.getId(), List.of()),
                properties.maxAttempts()))
            .toList();
        String next = rows.size() > pageSize ? new PageRequestValues.IdCursor(page.getLast().getId()).encode() : null;
        return new AdminReviewPage(items, next);
    }

    /**
     * @param visibility HIDDEN(숨김 확정) · VISIBLE(다시 공개). 다른 값이면 INVALID_VISIBILITY. 없는 리뷰면 REVIEW_NOT_FOUND
     */
    @Transactional
    public VisibilityChanged changeVisibility(Long adminId, Long reviewId, String visibility) {
        boolean hide = parseVisibility(visibility);
        Review review = reviews.findForUpdate(reviewId)
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));
        if (hide) {
            if (review.hideByAdmin(adminId, ReviewTimes.now())) {
                statsShare.remove(review);
            }
        } else if (review.restoreByAdmin(adminId, ReviewTimes.now())) {
            statsShare.add(review);
        }
        return new VisibilityChanged(review.getId(), review.getModerationStatus().name());
    }

    // 상태 이름과 겹치지 않는 운영 조회용 이름(재시도 상한에 닿은 대기)
    private static final String PENDING_EXHAUSTED = "PENDING_EXHAUSTED";

    private static ModerationStatus parseStatus(String status) {
        return Arrays.stream(ModerationStatus.values()).filter(value -> value.name().equals(status)).findFirst()
            .orElseThrow(() -> new BusinessException(ReviewErrorCode.INVALID_MODERATION_STATUS));
    }

    private static boolean parseVisibility(String visibility) {
        if ("HIDDEN".equals(visibility)) {
            return true;
        }
        if ("VISIBLE".equals(visibility)) {
            return false;
        }
        throw new BusinessException(ReviewErrorCode.INVALID_VISIBILITY);
    }

    /**
     * @param nextCursor 다음 쪽 커서. 마지막 쪽이면 null
     */
    public record AdminReviewPage(List<AdminReviewItem> items, String nextCursor) {
    }

    /**
     * 관리자 판단용 리뷰 한 건.
     *
     * @param status 검사 상태 이름
     * @param reason 걸린 이유 분류. 없으면 null
     * @param attemptsExhausted 재시도 상한에 닿아 대기 목록에서 빠진 검사 대기 리뷰인지(공개 그대로)
     */
    public record AdminReviewItem(Long reviewId, Long productId, String status, String reason,
                                  LocalDateTime moderatedAt, int attempts, boolean attemptsExhausted, String content,
                                  List<String> imageUrls, LocalDateTime createdAt) {

        static AdminReviewItem from(AdminReviewRow row, List<String> imageUrls, int maxAttempts) {
            return new AdminReviewItem(row.getId(), row.getProductId(), row.getModerationStatus().name(),
                row.getModerationReason() == null ? null : row.getModerationReason().name(), row.getModeratedAt(),
                row.getModerationAttempts(),
                row.getModerationStatus() == ModerationStatus.PENDING && row.getModerationAttempts() >= maxAttempts,
                row.getContent(), List.copyOf(imageUrls), row.getCreatedAt());
        }
    }

    public record VisibilityChanged(Long reviewId, String status) {
    }

    /**
     * @param counts 상태 이름(PENDING · PENDING_EXHAUSTED · PASSED · HIDDEN · CONFIRMED_HIDDEN · RESTORED) → 리뷰 수
     * @param oldestPendingCreatedAt 대기 목록에서 가장 오래된 리뷰의 작성 시각. 없으면 null
     */
    public record ModerationOverview(Map<String, Long> counts, LocalDateTime oldestPendingCreatedAt,
                                     List<RecentHidden> recentHidden) {

        public ModerationOverview {
            counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
            recentHidden = List.copyOf(recentHidden);
        }
    }

    /**
     * @param reason 이유 분류 이름. 없으면 null
     */
    public record RecentHidden(Long reviewId, String reason, LocalDateTime moderatedAt) {
    }
}
