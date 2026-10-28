package com.bbcc.kidly.boundedcontext.payment.out.toss;

import com.bbcc.kidly.boundedcontext.payment.app.port.PaymentGateway;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class TossClientConfig {

    @Bean
    PaymentGateway paymentGateway(TossProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return new TossPaymentGateway(RestClient.builder().requestFactory(requestFactory), properties);
    }
}
