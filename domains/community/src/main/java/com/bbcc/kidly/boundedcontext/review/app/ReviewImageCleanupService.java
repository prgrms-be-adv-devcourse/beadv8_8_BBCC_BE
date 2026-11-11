package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.boundedcontext.review.app.port.ImageCleanupRunLock;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.Deletion;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.ListUnavailableException;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.StoredObject;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.StoredPage;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanups;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImages;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageCleanup;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewTimes;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 쓰이지 않는 리뷰 사진을 저장소에서 지운다(스케줄러가 부른다). 한 주기는 정리 대기 처리 → 훑기 한 쪽 순서이고, 서버 한 대에서만 돈다
 * (잠금을 못 잡으면 건너뜀). 저장소에 닿지 않는 설정이면 돌지 않는다.
 * <ul>
 *   <li>정리 대기: 차례가 된 행을 batch-size만큼. 정리할 수 있는 key 모양이 아니면 지우지 않고 행만 지운다. 지우기 직전에 그 주소가 지금
 *       리뷰에 붙어 있으면 지우지 않고 행만 지운다. 저장소 호출은 트랜잭션 밖이고, 결과 반영은 한 건 = 트랜잭션 하나(읽은 시도 횟수가
 *       그대로일 때만). 실패는 시도 +1 · 다음 시각 = 지금 + 1분 × 2^(시도−1)(상한 max-backoff) · 실패 종류, 시도 상한은 없다</li>
 *   <li>훑기: 이 환경 리뷰 경로를 한 쪽 읽어, 올라간 지 orphan-grace가 지났고 정리 모양이며 어느 리뷰에도 붙지 않았고 대기 중도 아닌 파일만
 *       지운다(한 주기 성공한 삭제 scan-delete-limit까지, 지우지 못한 파일은 다음 바퀴에). 붙어 있는지 · 대기 중인지는 쪽마다 한 번씩 묶어 묻는다(파일 수와 상관없는 쿼리 수).
 *       이어 읽기 표지는 메모리에 두고, 삭제 상한에 닿으면 같은 쪽을 다음 주기에 다시 읽으며, 마지막 쪽 다음에는 처음부터 읽는다</li>
 * </ul>
 * 로그는 주기마다 한 줄이고 건수 · 결과 · 실패 종류만 남긴다(key · 주소를 넣지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewImageCleanupService {

    static final Duration FIRST_BACKOFF = Duration.ofMinutes(1);
    private static final int MAX_FAILURE_TYPE_LENGTH = 200;

    private final ReviewImageCleanups cleanups;
    private final ReviewImages reviewImages;
    private final ReviewImageCleanupStorage storage;
    private final ImageCleanupRunLock runLock;
    private final ReviewSeparateTransaction separateTransaction;
    private final ReviewImageCleanupProperties properties;

    // 훑기 이어 읽기 표지(서버 메모리). null이면 처음부터. 서버가 바뀌면 처음부터 다시 읽는다(지울 것만 지우므로 무해)
    private volatile String scanToken;

    public CleanupRun run() {
        if (!storage.available()) {
            return new CleanupRun(PendingRun.none(), SweepRun.none(), false, true);
        }
        AtomicReference<CleanupRun> result = new AtomicReference<>();
        if (!runLock.runExclusively(() -> result.set(new CleanupRun(processPending(), sweep(), false, false)))) {
            return new CleanupRun(PendingRun.none(), SweepRun.none(), true, false);
        }
        CleanupRun run = result.get();
        log.info("리뷰 사진 정리 pending(deleted={} keptAttached={} discarded={} failed={}) "
                + "sweep(listed={} deleted={} failed={} limitReached={} listFailed={}) backlog={} oldestQueuedAt={}",
            run.pending().deleted(), run.pending().keptAttached(), run.pending().discarded(), run.pending().failed(),
            run.sweep().listed(), run.sweep().deleted(), run.sweep().failed(), run.sweep().limitReached(),
            run.sweep().listFailed(), cleanups.count(), cleanups.findOldestCreatedAt());
        return run;
    }

    /**
     * 차례가 된 정리 대기를 처리한다(잠금은 run이 잡는다).
     */
    public PendingRun processPending() {
        LocalDateTime now = ReviewTimes.now();
        int deleted = 0;
        int keptAttached = 0;
        int discarded = 0;
        int failed = 0;
        for (ReviewImageCleanup row : cleanups.findDue(now, Limit.of(properties.batchSize()))) {
            switch (handle(row)) {
                case DELETED -> deleted++;
                case KEPT_ATTACHED -> keptAttached++;
                case DISCARDED -> discarded++;
                case FAILED -> failed++;
            }
        }
        return new PendingRun(deleted, keptAttached, discarded, failed);
    }

    private PendingOutcome handle(ReviewImageCleanup row) {
        String key = row.getObjectKey();
        if (!storage.isCleanable(key)) {
            finish(row);
            return PendingOutcome.DISCARDED;
        }
        if (reviewImages.existsByImageUrl(storage.imageUrlOf(key))) {
            finish(row);
            return PendingOutcome.KEPT_ATTACHED;
        }
        Deletion deletion = storage.delete(key);
        return switch (deletion.outcome()) {
            case DELETED -> {
                finish(row);
                yield PendingOutcome.DELETED;
            }
            case REFUSED -> {
                finish(row);
                yield PendingOutcome.DISCARDED;
            }
            case FAILED -> {
                int attempts = row.getAttempts() + 1;
                LocalDateTime next = ReviewTimes.now().plus(backoff(attempts));
                separateTransaction.run(() -> cleanups.recordFailure(row.getId(), row.getAttempts(), next,
                    shorten(deletion.failureType())));
                yield PendingOutcome.FAILED;
            }
        };
    }

    private void finish(ReviewImageCleanup row) {
        separateTransaction.run(() -> cleanups.finish(row.getId(), row.getAttempts()));
    }

    /**
     * 1분 × 2^(시도−1), max-backoff에서 멈춘다.
     */
    Duration backoff(int attempts) {
        Duration max = properties.maxBackoff();
        Duration delay = FIRST_BACKOFF;
        for (int i = 1; i < attempts && delay.compareTo(max) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(max) > 0 ? max : delay;
    }

    /**
     * 이 환경 리뷰 경로를 한 쪽 훑어 쓰이지 않는 파일을 지운다(잠금은 run이 잡는다).
     */
    public SweepRun sweep() {
        String token = scanToken;
        StoredPage page;
        try {
            page = storage.list(token, properties.scanPageSize());
        } catch (ListUnavailableException e) {
            return new SweepRun(0, 0, 0, false, true);
        }
        Instant cutoff = Instant.now().minus(properties.orphanGrace());
        List<String> candidates = page.objects().stream()
            .filter(object -> object.lastModified() != null && !object.lastModified().isAfter(cutoff))
            .map(StoredObject::key)
            .filter(storage::isCleanable)
            .toList();
        Set<String> keep = new HashSet<>();
        if (!candidates.isEmpty()) {
            Set<String> attached = new HashSet<>(reviewImages.findAttachedUrls(
                candidates.stream().map(storage::imageUrlOf).toList()));
            candidates.stream().filter(key -> attached.contains(storage.imageUrlOf(key))).forEach(keep::add);
            keep.addAll(cleanups.findQueuedKeys(candidates));
        }
        int deleted = 0;
        int failed = 0;
        boolean limitReached = false;
        for (String key : candidates) {
            if (keep.contains(key)) {
                continue;
            }
            // 상한에는 성공한 삭제만 센다. 지우지 못한 파일은 이번 바퀴에서 건너뛰고 다음 바퀴에 다시 본다
            // (늘 실패하는 파일이 많아도 한 쪽에 묶이지 않는다. 한 쪽의 시도는 쪽 크기로 묶인다)
            if (deleted >= properties.scanDeleteLimit()) {
                limitReached = true;
                break;
            }
            if (storage.delete(key).outcome() == Deletion.Outcome.DELETED) {
                deleted++;
            } else {
                failed++;
            }
        }
        // 성공한 삭제가 상한에 닿으면 같은 쪽을 다음에 다시 읽는다(지운 파일은 빠지므로 나아간다). 아니면 다음 쪽, 마지막 쪽이면 처음부터
        scanToken = limitReached ? token : page.nextContinuationToken();
        return new SweepRun(page.objects().size(), deleted, failed, limitReached, false);
    }

    void restartScan() {
        scanToken = null;
    }

    private static String shorten(String failureType) {
        if (failureType == null || failureType.length() <= MAX_FAILURE_TYPE_LENGTH) {
            return failureType;
        }
        return failureType.substring(0, MAX_FAILURE_TYPE_LENGTH);
    }

    private enum PendingOutcome {
        DELETED, KEPT_ATTACHED, DISCARDED, FAILED
    }

    /**
     * 한 주기 결과.
     *
     * @param skipped 다른 서버가 정리 중이라 건너뛰었는지
     * @param unavailable 저장소에 닿지 않는 설정이라 돌지 않았는지
     */
    public record CleanupRun(PendingRun pending, SweepRun sweep, boolean skipped, boolean unavailable) {
    }

    /**
     * 정리 대기 처리 결과.
     *
     * @param keptAttached 지금 리뷰에 붙어 있어 지우지 않고 대기만 지운 수
     * @param discarded 정리할 수 있는 key 모양이 아니라 지우지 않고 대기만 지운 수
     * @param failed 실패해 다음 시각으로 미룬 수
     */
    public record PendingRun(int deleted, int keptAttached, int discarded, int failed) {

        static PendingRun none() {
            return new PendingRun(0, 0, 0, 0);
        }
    }

    /**
     * 훑기 결과.
     *
     * @param listed 이번에 읽은 저장소 목록 수
     * @param limitReached 한 주기 삭제 상한에 닿아 같은 쪽을 다음 주기에 다시 읽는지
     * @param listFailed 목록을 읽지 못했는지
     */
    public record SweepRun(int listed, int deleted, int failed, boolean limitReached, boolean listFailed) {

        static SweepRun none() {
            return new SweepRun(0, 0, 0, false, false);
        }
    }
}
