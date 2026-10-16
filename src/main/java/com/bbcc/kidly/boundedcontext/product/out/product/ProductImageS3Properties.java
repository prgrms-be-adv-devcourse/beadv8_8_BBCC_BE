package com.bbcc.kidly.boundedcontext.product.out.product;

public record ProductImageS3Properties(String bucket, String region, String keyPrefix, boolean fakeCredentials) {
}
