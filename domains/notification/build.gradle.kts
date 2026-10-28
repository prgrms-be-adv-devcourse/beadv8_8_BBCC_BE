plugins {
    id("kidly.domain")
}

dependencies {
    // 메일 발송 (Gmail SMTP). kidly.mail.enabled가 false면 보내지 않고 로그만 남긴다
    implementation("org.springframework.boot:spring-boot-starter-mail")
}
