package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.ReviewModerationRecorder.Recorded;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Content;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Verdict;
import com.bbcc.kidly.boundedcontext.review.app.port.ModerationRunLock;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImages;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerations;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewModerations.ModerationCandidateRow;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 검사 대기 리뷰를 한 주기 분량 검사한다(스케줄러가 부른다). 서버 한 대에서만 돌고(잠금을 못 잡으면 건너뜀),
 * 검사기 호출은 트랜잭션 밖에서, 결과 반영은 한 건 = 트랜잭션 하나(ReviewModerationRecorder)로 한다.
 * 한도 초과(429)면 그 주기의 남은 건을 멈춘다. 로그는 리뷰 한 건에 한 줄이고 본문 · 사진 주소를 넣지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewModerationService {

    private final ReviewModerations reviewModerations;
    private final ReviewImages reviewImages;
    private final ContentModerator contentModerator;
    private final ReviewModerationRecorder recorder;
    private final ModerationRunLock runLock;
    private final ReviewModerationProperties properties;
    private final CommunityMetrics metrics;

    public ModerationRun processPending() {
        AtomicReference<ModerationRun> run = new AtomicReference<>();
        if (!runLock.runExclusively(() -> run.set(processBatch()))) {
            return ModerationRun.skippedByLock();
        }
        return run.get();
    }

    /**
     * 이번 주기에 검사할 대기 리뷰(id 순, batch-size까지, 재시도 상한 미만). 사진은 한 번에 읽는다.
     */
    public List<Candidate> pendingBatch() {
        List<ModerationCandidateRow> rows = reviewModerations.findModerationCandidates(properties.maxAttempts(),
            Limit.of(properties.batchSize()));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> images = reviewImages.findByReviewIdInOrderByReviewIdAscPositionAsc(
                rows.stream().map(ModerationCandidateRow::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(ReviewImage::getReviewId,
                Collectors.mapping(ReviewImage::getImageUrl, Collectors.toList())));
        return rows.stream()
            .map(row -> new Candidate(row.getId(), row.getContentVersion(), row.getModerationAttempts(),
                new Content(row.getContent(), images.getOrDefault(row.getId(), List.of()))))
            .toList();
    }

    private ModerationRun processBatch() {
        int passed = 0;
        int hidden = 0;
        int undecided = 0;
        int discarded = 0;
        boolean rateLimited = false;
        for (Candidate candidate : pendingBatch()) {
            Result result = moderate(candidate);
            // 검사기 호출마다 센다(반영 여부와 상관없다)
            metrics.recordModerationCheck(result);
            Recorded recorded = recorder.record(candidate, result);
            log.info("리뷰 검사 reviewId={} recorded={} reason={} cause={}", candidate.reviewId(), recorded,
                result.reason(), result.cause());
            switch (recorded) {
                case PASSED -> passed++;
                case HIDDEN -> hidden++;
                case UNDECIDED -> undecided++;
                case DISCARDED -> discarded++;
            }
            if (result.verdict() == Verdict.UNDECIDED && result.cause() == FailureCause.RATE_LIMITED) {
                rateLimited = true;
                break;
            }
        }
        return new ModerationRun(passed, hidden, undecided, discarded, rateLimited, false);
    }

    // 검사기는 예외를 던지지 않는 계약이지만, 그래도 나오면 판단 못 함으로 다룬다(예외 종류만 남긴다)
    private Result moderate(Candidate candidate) {
        try {
            return contentModerator.moderate(candidate.content());
        } catch (RuntimeException e) {
            log.warn("리뷰 검사기가 예외로 끝났습니다. reviewId={} error={}", candidate.reviewId(),
                e.getClass().getSimpleName());
            return Result.undecided(FailureCause.UNAVAILABLE);
        }
    }

    /**
     * 검사할 리뷰 한 건. 버전 · 시도 횟수는 결과를 반영할 때 그 사이 바뀌었는지 보는 기준이다.
     */
    public record Candidate(Long reviewId, long contentVersion, int attempts, Content content) {
    }

    /**
     * 한 주기 결과.
     *
     * @param rateLimited 한도 초과로 남은 건을 멈췄는지
     * @param skipped 다른 서버가 검사 중이라 건너뛰었는지
     */
    public record ModerationRun(int passed, int hidden, int undecided, int discarded, boolean rateLimited,
                                boolean skipped) {

        static ModerationRun skippedByLock() {
            return new ModerationRun(0, 0, 0, 0, false, true);
        }
    }
}
