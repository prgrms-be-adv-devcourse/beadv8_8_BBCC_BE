package com.bbcc.kidly.boundedcontext.notification.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 알림 발송의 Kidly 정책. 재시도 간격 · 멈춘 발송을 다시 맡는 기준 · 보내지 않는 주소를 정한다.
 */
public final class NotificationPolicy {

    // 만들어진 뒤 이만큼 PENDING이면 비동기로 넘기기 전에 서버가 멈춘 것으로 본다
    public static final Duration PENDING_TIMEOUT = Duration.ofMinutes(1);

    // 발송을 맡은 채로 이보다 오래 끝나지 않으면 서버가 발송 중에 멈춘 것으로 보고 다시 맡는다
    public static final Duration SENDING_TIMEOUT = Duration.ofMinutes(5);

    private static final List<Duration> RETRY_DELAYS =
        List.of(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10));

    // 첫 시도 + 재시도 3번
    public static final int MAX_ATTEMPTS = RETRY_DELAYS.size() + 1;

    // RFC 2606 · 6761의 예약 도메인. 실제로 받을 수 없어 보내지 않는다 (초기 데이터 kidly.test, 테스트 example.com)
    private static final List<String> RESERVED_TLDS = List.of("test", "example", "invalid", "localhost");
    private static final Set<String> RESERVED_DOMAINS = Set.of("example.com", "example.net", "example.org");

    private NotificationPolicy() {
    }

    // 재시도할 수 있고 시도 횟수가 남았으면 다음 시도 시각을 30초 → 2분 → 10분 뒤로 잡는다. 더 시도하지 않으면 null
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
