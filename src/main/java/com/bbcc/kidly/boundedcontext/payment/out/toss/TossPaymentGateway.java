package com.bbcc.kidly.boundedcontext.payment.out;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgApproval;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgCancellation;
import com.bbcc.kidly.boundedcontext.payment.app.port.PgResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 토스페이먼츠 결제 승인·취소·조회 API 클라이언트.
 * 시간 초과와 5xx는 토스에서 처리됐는지 알 수 없으므로 실패가 아닌 모름으로 돌려준다.
 */
@Slf4j
public class TossPaymentGateway implements PaymentGateway {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String DONE = "DONE";
    // 승인 전이거나 승인 중이라 조회만으로는 결과를 확정할 수 없는 결제 상태
    private static final Set<String> PENDING_STATUSES = Set.of("READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT");
    // 4xx지만 앞선 요청이 처리됐거나 처리 중일 수 있는 오류. 조회로 확정해야 한다
    private static final Set<String> UNCERTAIN_ERROR_CODES =
        Set.of("ALREADY_PROCESSED_PAYMENT", "IDEMPOTENT_REQUEST_PROCESSING");

    private final RestClient restClient;

    public TossPaymentGateway(RestClient.Builder builder, TossProperties properties) {
        this.restClient = builder
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth(properties.secretKey()))
            .build();
    }

    @Override
    public PgResult<PgApproval> confirm(String paymentKey, String orderId, long amount, String idempotencyKey) {
        RestClient.RequestHeadersSpec<?> request = restClient.post()
            .uri("/v1/payments/confirm")
            .header(IDEMPOTENCY_KEY, idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount));
        return call(request, payment -> new PgResult.Success<>(payment.toApproval()));
    }

    @Override
    public PgResult<PgCancellation> cancel(String paymentKey, String reason, String idempotencyKey) {
        RestClient.RequestHeadersSpec<?> request = restClient.post()
            .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
            .header(IDEMPOTENCY_KEY, idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("cancelReason", reason));
        return call(request, payment -> new PgResult.Success<>(new PgCancellation(payment.lastCanceledAt())));
    }

    @Override
    public PgResult<PgApproval> findByOrderId(String orderId) {
        RestClient.RequestHeadersSpec<?> request = restClient.get()
            .uri("/v1/payments/orders/{orderId}", orderId);
        return call(request, payment -> {
            if (DONE.equals(payment.status())) {
                return new PgResult.Success<>(payment.toApproval());
            }
            if (PENDING_STATUSES.contains(payment.status())) {
                return new PgResult.Unknown<>("결제 진행 중: " + payment.status());
            }
            return new PgResult.Rejected<>(payment.status(), "승인되지 않고 끝난 결제");
        });
    }

    private <T> PgResult<T> call(RestClient.RequestHeadersSpec<?> request,
                                 Function<TossPayment, PgResult<T>> onSuccess) {
        try {
            return request.exchange((req, response) -> {
                HttpStatusCode status = response.getStatusCode();
                if (status.is2xxSuccessful()) {
                    return onSuccess.apply(response.bodyTo(TossPayment.class));
                }
                if (status.is5xxServerError()) {
                    return new PgResult.Unknown<>("토스 서버 오류: " + status.value());
                }
                return clientError(status, response.bodyTo(TossError.class));
            });
        } catch (RestClientException e) {
            // 시간 초과·연결 실패, 응답을 읽지 못한 경우
            log.warn("토스 API 호출 결과를 알 수 없습니다", e);
            return new PgResult.Unknown<>(e.getMessage());
        }
    }

    private static <T> PgResult<T> clientError(HttpStatusCode status, TossError error) {
        if (error == null || error.code() == null) {
            return new PgResult.Rejected<>("HTTP_" + status.value(), "토스 오류 응답에 코드가 없습니다");
        }
        if (UNCERTAIN_ERROR_CODES.contains(error.code())) {
            return new PgResult.Unknown<>(error.code());
        }
        return new PgResult.Rejected<>(error.code(), error.message());
    }

    // 토스 인증은 시크릿 키 뒤에 콜론을 붙여 Base64로 인코딩한다 (비밀번호 없음)
    private static String basicAuth(String secretKey) {
        String credentials = secretKey + ":";
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    // 토스 시각은 +09:00이므로 서버 기본 시간대로 바꿔 LocalDateTime.now()와 비교할 수 있게 한다
    private static LocalDateTime toLocal(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossPayment(String paymentKey, String status, long totalAmount, OffsetDateTime approvedAt,
                       List<TossCancel> cancels) {

        PgApproval toApproval() {
            return new PgApproval(paymentKey, totalAmount, toLocal(approvedAt));
        }

        LocalDateTime lastCanceledAt() {
            return cancels == null || cancels.isEmpty() ? null : toLocal(cancels.getLast().canceledAt());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossCancel(OffsetDateTime canceledAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossError(String code, String message) {
    }
}
