package com.bbcc.kidly.boundedcontext.notification.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NotificationPolicy {

    // 만들어진 뒤 이만큼 PENDING이면 비동기로 넘기기 전에 서버가 멈춘 것으로 본다
    public static final Duration PENDING_TIMEOUT = Duration.ofMinutes(1);

    // 발송을 맡은 채로 이보다 오래 끝나지 않으면 서버가 발송 중에 멈춘 것으로 보고 다시 맡는다
    public static final Duration SENDING_TIMEOUT = Duration.ofMinutes(5);

    private static final List<Duration> RETRY_DELAYS =
        List.of(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10));

    public static final int MAX_ATTEMPTS = RETRY_DELAYS.size() + 1;

    public static final Duration INBOX_RETENTION = Duration.ofDays(90);

    // 끝난 발송 기록(받는 주소, 본문)을 남겨 두는 기간. 발송 문의(CS)에 답할 수 있게 1년 두고, 지나면 정리 배치가 지운다
    public static final Duration HISTORY_RETENTION = Duration.ofDays(365);

    public static final int PUSH_FID_LIMIT_PER_MEMBER = 10;

    // 이 기간 동안 다시 등록되지 않은 푸시 FID는 쓰지 않는 브라우저로 보고 지운다(FCM은 270일 동안 연결하지 않은 등록을 만료시킨다)
    public static final Duration PUSH_FID_RETENTION = Duration.ofDays(270);

    // RFC 2606, 6761의 예약 도메인. 실제로 받을 수 없어 보내지 않는다 (초기 데이터 kidly.test, 테스트 example.com)
    private static final List<String> RESERVED_TLDS = List.of("test", "example", "invalid", "localhost");
    private static final Set<String> RESERVED_DOMAINS = Set.of("example.com", "example.net", "example.org");

    private NotificationPolicy() {
    }

    public static LocalDateTime nextAttemptAt(int attemptCount, boolean retryable, LocalDateTime now) {
        if (!retryable || attemptCount >= MAX_ATTEMPTS) {
            return null;
        }
        return now.plus(RETRY_DELAYS.get(attemptCount - 1));
    }

    public static boolean isDeliverableEmail(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return false;
        }
        var address = recipient.strip();
        if (!address.contains("@")) {
            return false;
        }
        var rawDomain = address.substring(address.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        var domain = rawDomain.endsWith(".") ? rawDomain.substring(0, rawDomain.length() - 1) : rawDomain;
        return !RESERVED_DOMAINS.contains(domain)
            && RESERVED_TLDS.stream().noneMatch(tld -> domain.equals(tld) || domain.endsWith("." + tld));
    }
}
