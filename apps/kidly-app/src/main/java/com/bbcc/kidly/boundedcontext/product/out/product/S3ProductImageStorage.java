package com.bbcc.kidly.boundedcontext.product.out.product;

import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageStorage;
import com.bbcc.kidly.boundedcontext.product.app.port.product.ProductImageUploadUrl;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductImageUpload;
import java.time.Duration;
import java.util.UUID;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

public class S3ProductImageStorage implements ProductImageStorage {

    private static final Duration SIGNATURE_DURATION = Duration.ofMinutes(5);

    private final S3Presigner presigner;
    private final ProductImageS3Properties properties;

    public S3ProductImageStorage(S3Presigner presigner, ProductImageS3Properties properties) {
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public ProductImageUploadUrl issueUploadUrl(ProductImageUpload upload, long sellerId) {
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
        return new ProductImageUploadUrl(presigned.url().toString(), imageUrl(key), presigned.expiration());
    }

    @Override
    public String ownImageUrlPrefix(long sellerId) {
        return imageUrl(ProductImageUpload.keyDirectory(properties.keyPrefix(), sellerId));
    }

    // 버킷의 상품 이미지 경로는 공개 읽기라서 서명 없는 주소로 화면에 보여 줄 수 있다
    private String imageUrl(String key) {
        return "https://" + properties.bucket() + ".s3." + properties.region() + ".amazonaws.com/" + key;
    }
}
