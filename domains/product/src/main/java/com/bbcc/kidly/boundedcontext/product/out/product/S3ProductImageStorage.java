package com.bbcc.kidly.boundedcontext.product.out.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageStorage;
import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageUploadUrl;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUpload;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@RequiredArgsConstructor
public class S3ProductImageStorage implements ProductImageStorage {

    private static final Duration SIGNATURE_DURATION = Duration.ofMinutes(5);

    private final S3Presigner presigner;
    private final ProductImageS3Properties properties;

    @Override
    public ProductImageUploadUrl issueUploadUrl(ProductImageUpload upload, long sellerId) {
        var key = upload.key(properties.keyPrefix(), sellerId, UUID.randomUUID());
        var putObject = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .contentType(upload.format().contentType())
            .contentLength(upload.contentLength())
            .build();
        var presigned = presigner.presignPutObject(request -> request
            .signatureDuration(SIGNATURE_DURATION)
            .putObjectRequest(putObject));
        return new ProductImageUploadUrl(presigned.url().toString(), imageUrl(key), presigned.expiration());
    }

    @Override
    public String ownImageUrlPrefix(long sellerId) {
        return imageUrl(ProductImageUpload.keyDirectory(properties.keyPrefix(), sellerId));
    }

    private String imageUrl(String key) {
        return "https://" + properties.bucket() + ".s3." + properties.region() + ".amazonaws.com/" + key;
    }
}
