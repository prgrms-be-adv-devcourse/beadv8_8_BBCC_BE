package com.bbcc.kidly.boundedcontext.product.out.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageUploadUrl;
import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageUploadUrlIssuer;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUpload;
import java.time.Duration;
import java.util.UUID;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

public class S3ProductImageUploadUrlIssuer implements ProductImageUploadUrlIssuer {

    private static final Duration SIGNATURE_DURATION = Duration.ofMinutes(5);

    private final S3Presigner presigner;
    private final ProductImageS3Properties properties;

    public S3ProductImageUploadUrlIssuer(S3Presigner presigner, ProductImageS3Properties properties) {
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public ProductImageUploadUrl issue(ProductImageUpload upload, long sellerId) {
        String key = upload.key(properties.keyPrefix(), sellerId, UUID.randomUUID());
        PutObjectRequest putObject = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .contentType(upload.format().contentType())
            .contentLength(upload.contentLength())
            .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(request -> request
            .signatureDuration(SIGNATURE_DURATION)
            .putObjectRequest(putObject));
        return new ProductImageUploadUrl(presigned.url().toString(), null, null);
    }
}
