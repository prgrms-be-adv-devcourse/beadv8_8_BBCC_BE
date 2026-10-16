package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.product.ProductFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageUploadUrl;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUpload;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUrls;
import com.bbcc.kidly.boundedcontext.product.out.product.ProductImageS3Properties;
import com.bbcc.kidly.boundedcontext.product.out.product.S3ProductImageUploadUrlIssuer;
import com.bbcc.kidly.global.error.BusinessException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ProductImageUploadUrlIssuerTest {

    private static final ProductImageS3Properties PROPERTIES =
        new ProductImageS3Properties("team04-kidly-s3", "ap-northeast-2", "dev", true);

    // 서명은 자격 증명으로 계산만 하므로 가짜 자격 증명으로도 AWS에 요청하지 않고 주소를 만든다
    private final S3Presigner presigner = S3Presigner.builder()
        .region(Region.AP_NORTHEAST_2)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .build();

    private final S3ProductImageUploadUrlIssuer issuer = new S3ProductImageUploadUrlIssuer(presigner, PROPERTIES);

    @Test
    @DisplayName("업로드 주소는 버킷의 {환경}/products/{판매자 ID}/{UUID}.{확장자} 객체를 가리킨다")
    void uploadUrlPointsToObjectKey() {
        ProductImageUploadUrl issued = issuer.issue(ProductImageUpload.of("image/jpeg", 1_000L), SELLER_ID);

        URI uploadUrl = URI.create(issued.uploadUrl());
        assertThat(uploadUrl.getScheme()).isEqualTo("https");
        assertThat(uploadUrl.getHost()).isEqualTo("team04-kidly-s3.s3.ap-northeast-2.amazonaws.com");
        assertThat(uploadUrl.getPath()).matches("/dev/products/2/[0-9a-f-]{36}\\.jpg");
    }

    @Test
    @DisplayName("판매자 본인 경로는 버킷 공개 주소의 {환경}/products/{판매자 ID}/이다")
    void ownImageUrlPrefixIsSellerDirectory() {
        assertThat(issuer.ownImageUrlPrefix(SELLER_ID))
            .isEqualTo("https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/dev/products/2/");
    }

    @Test
    @DisplayName("발급한 이미지 주소는 같은 판매자의 본인 경로 검사를 통과하고 다른 판매자의 경로로는 거절된다")
    void issuedImageUrlPassesOnlyOwnPrefix() {
        String imageUrl = issuer.issue(ProductImageUpload.of("image/webp", 1_000L), SELLER_ID).imageUrl();

        assertThatCode(() -> ProductImageUrls.validate(null, List.of(imageUrl), issuer.ownImageUrlPrefix(SELLER_ID)))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> ProductImageUrls.validate(null, List.of(imageUrl), issuer.ownImageUrlPrefix(3L)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("업로드 주소는 5분 동안 유효하다")
    void uploadUrlExpiresInFiveMinutes() {
        ProductImageUploadUrl issued = issuer.issue(ProductImageUpload.of("image/jpeg", 1_000L), SELLER_ID);

        assertThat(issued.uploadUrl()).contains("X-Amz-Expires=300");
    }

    @Test
    @DisplayName("업로드 주소의 서명에 Content-Type과 Content-Length를 넣어 다른 형식이나 크기의 파일은 올라가지 않는다")
    void uploadUrlSignsContentTypeAndLength() {
        ProductImageUploadUrl issued = issuer.issue(ProductImageUpload.of("image/png", 1_234_567L), SELLER_ID);

        assertThat(signedHeaders(issued.uploadUrl())).contains("content-type", "content-length");
    }

    @Test
    @DisplayName("이미지 주소는 업로드한 객체의 공개 주소이고 서명 쿼리를 담지 않는다")
    void imageUrlIsPublicUrlOfSameObject() {
        ProductImageUploadUrl issued = issuer.issue(ProductImageUpload.of("image/webp", 1_000L), SELLER_ID);

        assertThat(issued.imageUrl())
            .matches("https://team04-kidly-s3\\.s3\\.ap-northeast-2\\.amazonaws\\.com/dev/products/2/[0-9a-f-]{36}\\.webp")
            .endsWith(URI.create(issued.uploadUrl()).getPath());
    }

    @Test
    @DisplayName("만료 시각은 발급한 때부터 5분 뒤다")
    void expiresAtIsFiveMinutesAfterIssue() {
        Instant before = Instant.now();
        ProductImageUploadUrl issued = issuer.issue(ProductImageUpload.of("image/jpeg", 1_000L), SELLER_ID);
        Instant after = Instant.now();

        assertThat(issued.expiresAt()).isBetween(
            before.plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.SECONDS),
            after.plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("같은 판매자가 같은 형식으로 두 번 발급해도 서로 다른 객체를 가리켜 덮어쓰지 않는다")
    void issuesDifferentObjectEachTime() {
        ProductImageUpload upload = ProductImageUpload.of("image/jpeg", 1_000L);

        ProductImageUploadUrl first = issuer.issue(upload, SELLER_ID);
        ProductImageUploadUrl second = issuer.issue(upload, SELLER_ID);

        assertThat(first.imageUrl()).isNotEqualTo(second.imageUrl());
    }

    private static List<String> signedHeaders(String url) {
        String query = URI.create(url).getRawQuery();
        for (String pair : query.split("&")) {
            if (pair.startsWith("X-Amz-SignedHeaders=")) {
                String encoded = pair.substring("X-Amz-SignedHeaders=".length());
                return List.of(URLDecoder.decode(encoded, StandardCharsets.UTF_8).split(";"));
            }
        }
        return List.of();
    }
}
