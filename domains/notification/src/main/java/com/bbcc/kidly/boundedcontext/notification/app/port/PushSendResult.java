package com.bbcc.kidly.boundedcontext.notification.app.port;

import java.util.List;

/**
 * 성공도 실패도 없으면 보내지 않은 것이다(발송을 끈 환경).
 */
public record PushSendResult(int successCount, List<Failure> failures) {

    public PushSendResult {
        failures = List.copyOf(failures);
    }

    public static PushSendResult notSent() {
        return new PushSendResult(0, List.of());
    }

    public record Failure(String fid, PushFailure reason) {

        @Override
        public String toString() {
            return "Failure[reason=" + reason + "]";
        }
    }

    /**
     * 아무 데도 못 보냈고 모든 FID가 INVALID_ARGUMENT면 FID보다 메시지가
     * 잘못됐을 수 있어 지우지 않는다.
     */
    public List<String> fidsToDelete() {
        if (allInvalidArgument()) {
            return List.of();
        }
        return failures.stream()
            .filter(f -> f.reason() == PushFailure.UNREGISTERED || f.reason() == PushFailure.INVALID_ARGUMENT)
            .map(Failure::fid)
            .toList();
    }

    public Outcome outcome() {
        if (successCount > 0) {
            return Outcome.SENT;
        }
        if (failures.isEmpty()) {
            return Outcome.SKIPPED;
        }
        if (allInvalidArgument()) {
            return Outcome.FAILED;
        }
        if (has(PushFailure.RETRYABLE)) {
            return Outcome.RETRYABLE_FAILED;
        }
        if (has(PushFailure.REJECTED)) {
            return Outcome.FAILED;
        }
        return Outcome.SKIPPED;
    }

    // 알림에 남길 실패 사유. FID는 넣지 않는다
    public String failureReason() {
        if (allInvalidArgument()) {
            return "FCM: 모든 FID가 INVALID_ARGUMENT";
        }
        return "FCM: " + failures.stream().map(f -> f.reason().name()).distinct().sorted().toList();
    }

    private boolean allInvalidArgument() {
        return successCount == 0 && !failures.isEmpty()
            && failures.stream().allMatch(f -> f.reason() == PushFailure.INVALID_ARGUMENT);
    }

    private boolean has(PushFailure reason) {
        return failures.stream().anyMatch(f -> f.reason() == reason);
    }

    public enum Outcome {
        SENT,
        SKIPPED,
        RETRYABLE_FAILED,
        FAILED
    }
}
