plugins {
    id("kidly.domain")
}

dependencies {
    // 메일 발송 (Gmail SMTP). kidly.mail.enabled가 false면 보내지 않고 로그만 남긴다
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // 웹 푸시 발송 (FCM HTTP v1). 메시징만 쓰므로 Firestore · Cloud Storage 클라이언트(gRPC 포함)는 빼고 받는다
    implementation(libs.firebase.admin) {
        exclude(group = "com.google.cloud", module = "google-cloud-firestore")
        exclude(group = "com.google.cloud", module = "google-cloud-storage")
    }
    // FirebaseOptions가 실행 중에 JacksonFactory를 쓴다. 위에서 뺀 Cloud Storage를 거쳐 들어오던 것이라 직접 받는다
    runtimeOnly(libs.google.http.client.jackson2)
    // 발송 지표(app.NotificationMetrics). 레지스트리는 앱이 준다(notification-service: actuator + Prometheus). 버전은 Boot BOM
    implementation("io.micrometer:micrometer-core")
}
