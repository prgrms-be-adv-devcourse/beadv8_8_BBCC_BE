plugins {
    id("kidly.domain")
}

dependencies {
    // 상품 이미지를 브라우저가 S3에 직접 올리도록 업로드 주소(Presigned URL)를 만든다 (S3Presigner)
    implementation(libs.aws.s3)
}
