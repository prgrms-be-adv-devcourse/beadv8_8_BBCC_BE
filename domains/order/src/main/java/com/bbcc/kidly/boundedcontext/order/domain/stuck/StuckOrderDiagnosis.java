package com.bbcc.kidly.boundedcontext.order.domain.stuck;

import com.bbcc.kidly.boundedcontext.order.domain.OrderTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 멈춘 주문 AI 진단 메모(#926). AI는 제안만 하고 조치는 관리자가 기존 API로 한다.
 * (주문, 멈춘 종류, 그때 마지막 상태 이력 ID)당 한 줄이라 상태가 바뀌기 전까지 다시 요청하지 않는다. 실패도 한 줄로 남긴다.
 */
@Entity
@Table(schema = "orders", name = "order_stuck_diagnosis",
    uniqueConstraints = @UniqueConstraint(name = StuckOrderDiagnosis.STATE_UNIQUE,
        columnNames = {"order_id", "stuck_kind", "history_mark"}),
    indexes = @Index(name = "idx_order_stuck_diagnosis_requested_at", columnList = "requested_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StuckOrderDiagnosis extends OrderTimeEntity {

    public static final String STATE_UNIQUE = "uk_order_stuck_diagnosis_state";
    public static final int TEXT_MAX = 1000;

    public enum Status {
        DIAGNOSED,
        FAILED
    }

    public enum Confidence {
        LOW,
        MEDIUM,
        HIGH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 40)
    private String stuckKind;

    /** 요청할 때 그 주문의 마지막 상태 이력 ID(없으면 0). 상태가 바뀌면 값이 달라져 다시 요청할 수 있다 */
    @Column(nullable = false)
    private long historyMark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = TEXT_MAX)
    private String summary;

    @Column(length = TEXT_MAX)
    private String recommendedAction;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Confidence confidence;

    /** 실패 이유 코드(TIMEOUT · BAD_RESPONSE 등). 성공이면 null */
    @Column(length = 40)
    private String failureReason;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    public static StuckOrderDiagnosis diagnosed(Key key, String summary, String recommendedAction,
        Confidence confidence, Source source) {
        if (isBlank(summary) || isBlank(recommendedAction) || confidence == null) {
            throw new IllegalArgumentException("진단 메모에는 요약 · 권장 조치 · 확신도가 있어야 한다");
        }
        StuckOrderDiagnosis diagnosis = of(key, Status.DIAGNOSED, source);
        diagnosis.summary = cut(summary);
        diagnosis.recommendedAction = cut(recommendedAction);
        diagnosis.confidence = confidence;
        return diagnosis;
    }

    public static StuckOrderDiagnosis failed(Key key, String failureReason, Source source) {
        if (isBlank(failureReason)) {
            throw new IllegalArgumentException("실패 메모에는 실패 이유가 있어야 한다");
        }
        StuckOrderDiagnosis diagnosis = of(key, Status.FAILED, source);
        diagnosis.failureReason = failureReason.length() > 40 ? failureReason.substring(0, 40) : failureReason;
        return diagnosis;
    }

    private static StuckOrderDiagnosis of(Key key, Status status, Source source) {
        if (key == null || key.orderId() == null || isBlank(key.stuckKind()) || source == null
            || isBlank(source.provider()) || isBlank(source.model()) || source.requestedAt() == null) {
            throw new IllegalArgumentException("진단 메모의 주문 · 종류 · 제공자 · 모델 · 요청 시각은 필수다");
        }
        StuckOrderDiagnosis diagnosis = new StuckOrderDiagnosis();
        diagnosis.orderId = key.orderId();
        diagnosis.stuckKind = key.stuckKind();
        diagnosis.historyMark = key.historyMark();
        diagnosis.status = status;
        diagnosis.provider = cut(source.provider(), 100);
        diagnosis.model = cut(source.model(), 100);
        diagnosis.requestedAt = source.requestedAt();
        return diagnosis;
    }

    /**
     * 같은 상태 한 번을 가리키는 키.
     */
    public record Key(Long orderId, String stuckKind, long historyMark) {
    }

    /**
     * 누가 · 언제 답했는지(메모 표시용).
     */
    public record Source(String provider, String model, LocalDateTime requestedAt) {
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String cut(String value) {
        return cut(value.strip(), TEXT_MAX);
    }

    private static String cut(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
