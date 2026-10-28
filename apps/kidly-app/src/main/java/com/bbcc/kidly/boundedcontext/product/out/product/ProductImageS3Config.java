package com.bbcc.kidly.boundedcontext.product.out.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(ProductImageS3Properties.class)
public class ProductImageS3Config {

    @Bean
    S3Presigner productImageS3Presigner(ProductImageS3Properties properties) {
        return S3Presigner.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentialsProvider(properties))
            .build();
    }

    @Bean
    ProductImageStorage productImageStorage(
        S3Presigner productImageS3Presigner, ProductImageS3Properties properties) {
        return new S3ProductImageStorage(productImageS3Presigner, properties);
    }

    private AwsCredentialsProvider credentialsProvider(ProductImageS3Properties properties) {
        if (properties.fakeCredentials()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
