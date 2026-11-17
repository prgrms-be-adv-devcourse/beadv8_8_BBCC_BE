package com.bbcc.kidly.boundedcontext.product.out.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("kidly.product.image.s3")
public record ProductImageS3Properties(
    @NotBlank String bucket,
    @NotBlank String region,
    @NotBlank @Pattern(regexp = "[a-z]+") String keyPrefix,
    boolean fakeCredentials) {
}
