package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageStorage;
import com.bbcc.kidly.boundedcontext.product.out.product.ProductImageS3Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductImageS3ConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(ProductImageS3Config.class)
        .withPropertyValues(
            "kidly.product.image.s3.bucket=team04-kidly-s3",
            "kidly.product.image.s3.region=ap-northeast-2",
            "kidly.product.image.s3.fake-credentials=true");

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", "dev/", "/dev", "Dev"})
    @DisplayName("key prefix가 비었거나 소문자 말고 다른 글자를 담으면 기동에 실패한다")
    void failsOnInvalidKeyPrefix(String keyPrefix) {
        runner.withPropertyValues("kidly.product.image.s3.key-prefix=" + keyPrefix)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("설정이 맞으면 가짜 자격 증명으로 상품 이미지 저장소를 만든다")
    void createsStorageWithFakeCredentials() {
        runner.withPropertyValues("kidly.product.image.s3.key-prefix=local")
            .run(context -> assertThat(context).hasSingleBean(ProductImageStorage.class));
    }
}
