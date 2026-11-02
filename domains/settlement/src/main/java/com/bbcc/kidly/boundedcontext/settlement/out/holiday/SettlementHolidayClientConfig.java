package com.bbcc.kidly.boundedcontext.settlement.out.holiday;

import com.bbcc.kidly.boundedcontext.settlement.app.port.HolidayCalendar;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class SettlementHolidayClientConfig {

    @Bean
    HolidayCalendar settlementHolidayCalendar(SettlementHolidayProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return new KasiHolidayCalendar(RestClient.builder().requestFactory(requestFactory), properties);
    }
}
