package com.bbcc.kidly.boundedcontext.settlement.out.holiday;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 공휴일 특일정보 API(한국천문연구원) 설정. 기본값은 settlement.yml 한 곳에만 둔다.
 * 인증키는 비어 있어도 기동한다. 그때는 조회가 실패해 영업일 판단을 하지 못한다.
 *
 * @param baseUrl 특일정보 서비스 주소
 * @param serviceKey 공공데이터포털 인증키. 환경 변수 HOLIDAY_API_SERVICE_KEY로만 받는다
 * @param connectTimeout 연결 시간 제한. 1초 이상
 * @param readTimeout 응답 시간 제한. 1초 이상
 */
@Validated
@ConfigurationProperties("kidly.settlement.holiday")
public record SettlementHolidayProperties(
    @NotBlank String baseUrl,
    String serviceKey,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout) {

    private static final Duration MIN = Duration.ofSeconds(1);

    @AssertTrue(message = "kidly.settlement.holiday.connect-timeout은 1초 이상이어야 합니다")
    public boolean isConnectTimeoutAtLeastOneSecond() {
        return connectTimeout == null || connectTimeout.compareTo(MIN) >= 0;
    }

    @AssertTrue(message = "kidly.settlement.holiday.read-timeout은 1초 이상이어야 합니다")
    public boolean isReadTimeoutAtLeastOneSecond() {
        return readTimeout == null || readTimeout.compareTo(MIN) >= 0;
    }

    // 인증키가 로그에 찍히지 않게 가린다
    @Override
    public String toString() {
        return "SettlementHolidayProperties[baseUrl=" + baseUrl + ", serviceKey=***, connectTimeout="
            + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }
}
