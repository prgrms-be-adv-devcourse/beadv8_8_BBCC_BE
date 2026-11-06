package com.bbcc.kidly.boundedcontext.notification.out.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.notification.app.port.PushFailure;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.MessagingErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// 실제 FCM은 부르지 않는다. 오류 분류와 키 읽기 실패만 확인한다
class FcmPushSenderTest {

    @ParameterizedTest
    @CsvSource({
        "UNREGISTERED, UNREGISTERED",
        "INVALID_ARGUMENT, INVALID_ARGUMENT",
        "UNAVAILABLE, RETRYABLE",
        "INTERNAL, RETRYABLE",
        "QUOTA_EXCEEDED, RETRYABLE",
        "SENDER_ID_MISMATCH, REJECTED",
        "THIRD_PARTY_AUTH_ERROR, REJECTED"
    })
    @DisplayName("FCM 고유 오류 코드를 Kidly 실패 이유로 묶는다")
    void classifyMessagingErrorCode(MessagingErrorCode code, PushFailure expected) {
        assertThat(FcmPushSender.classify(code, ErrorCode.INVALID_ARGUMENT)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "UNAVAILABLE, RETRYABLE",
        "DEADLINE_EXCEEDED, RETRYABLE",
        "UNKNOWN, RETRYABLE",
        "NOT_FOUND, UNREGISTERED",
        "INVALID_ARGUMENT, INVALID_ARGUMENT",
        "PERMISSION_DENIED, REJECTED",
        "UNAUTHENTICATED, REJECTED"
    })
    @DisplayName("FCM 고유 코드가 없으면(네트워크 오류 · 서비스 계정 권한) 공통 코드로 정한다")
    void classifyErrorCode(ErrorCode code, PushFailure expected) {
        assertThat(FcmPushSender.classify(null, code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("키가 Base64가 아니거나 서비스 계정 키 JSON이 아니면 키 내용 없이 기동을 멈춘다")
    void invalidCredentials() {
        assertThatThrownBy(() -> FcmPushSender.fromBase64ServiceAccount("not base64 !!"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("FIREBASE_CREDENTIALS가 Base64 값이 아닙니다");
        var notServiceAccount = Base64.getEncoder().encodeToString("{\"type\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> FcmPushSender.fromBase64ServiceAccount(notServiceAccount))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("FIREBASE_CREDENTIALS를 서비스 계정 키 JSON으로 읽지 못했습니다")
            .hasNoCause();
    }
}
