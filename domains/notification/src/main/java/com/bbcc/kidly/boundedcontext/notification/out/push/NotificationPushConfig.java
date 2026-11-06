package com.bbcc.kidly.boundedcontext.notification.out.push;

import com.bbcc.kidly.boundedcontext.notification.app.port.NotificationPushSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 웹 푸시 발송기를 고른다. FCM 서비스 계정 키(FIREBASE_CREDENTIALS, 키 JSON의 Base64)가 있으면 FCM으로 보내고,
 * 없으면 앱은 그대로 띄우고 보내지 않는 발송기를 쓴다(푸시 알림은 SKIPPED). 키 값은 로그에 남기지 않는다.
 */
@Slf4j
@Configuration
public class NotificationPushConfig {

    @Bean
    public NotificationPushSender notificationPushSender(
        @Value("${kidly.notification.firebase-credentials:}") String firebaseCredentials) {
        if (!StringUtils.hasText(firebaseCredentials)) {
            log.warn("FCM 서비스 계정 키(FIREBASE_CREDENTIALS)가 없어 웹 푸시를 보내지 않고 로그만 남깁니다");
            return new LoggingPushSender();
        }
        return FcmPushSender.fromBase64ServiceAccount(firebaseCredentials);
    }
}
