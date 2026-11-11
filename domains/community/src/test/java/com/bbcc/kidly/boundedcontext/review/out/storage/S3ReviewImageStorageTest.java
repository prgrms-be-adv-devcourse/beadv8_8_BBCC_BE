package com.bbcc.kidly.boundedcontext.review.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageStorage.UploadUrl;
import com.bbcc.kidly.boundedcontext.review.domain.ReviewImageUpload;
import com.bbcc.kidly.boundedcontext.review.out.config.CommunityReviewImageStorageProperties;
import com.bbcc.kidly.boundedcontext.review.out.storage.ReviewImageUploadChecker.Presence;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 가짜 자격 증명으로 서명해 주소 모양만 본다(AWS에 요청하지 않는다).
 */
class S3ReviewImageStorageTest {

    private static final String BUCKET = "team04-kidly-s3";
    private static final String REGION = "ap-northeast-2";

    private final S3Presigner presigner = S3Presigner.builder()
        .region(Region.of(REGION))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
        .build();
    private final Map<String, Presence> presence = new HashMap<>();
    private final List<String> checkedKeys = new ArrayList<>();
    private final S3ReviewImageStorage storage = new S3ReviewImageStorage(presigner,
        new CommunityReviewImageStorageProperties(BUCKET, REGION, "dev", true, Duration.ofMinutes(5), "s3",
            Duration.ofSeconds(2)),
        new ReviewImageUploadChecker() {
            @Override
            public String name() {
                return "recording";
            }

            @Override
            public Presence check(String imageUrl, String key) {
                checkedKeys.add(key);
                return presence.getOrDefault(key, Presence.PRESENT);
            }
        });

    @AfterEach
    void close() {
        presigner.close();
    }

    @Test
    @DisplayName("업로드 주소: 우리 버킷 · 본인 경로 key에 PUT, content-type · content-length를 서명에 넣고 5분 뒤 만료")
    void signsPutWithTypeAndLength() {
        Instant before = Instant.now();

        UploadUrl issued = storage.issueUploadUrl(ReviewImageUpload.of("image/png", 1234L, 10_485_760L), 13L);

        URI upload = URI.create(issued.uploadUrl());
        assertThat(upload.getScheme()).isEqualTo("https");
        assertThat(upload.getHost()).isEqualTo(BUCKET + ".s3." + REGION + ".amazonaws.com");
        assertThat(upload.getPath()).matches("/dev/reviews/13/[0-9a-f-]{36}\\.png");
        assertThat(upload.getQuery()).contains("X-Amz-Expires=300")
            .contains("X-Amz-SignedHeaders=content-length;content-type;host");
        assertThat(issued.imageUrl()).isEqualTo("https://" + BUCKET + ".s3." + REGION + ".amazonaws.com"
            + upload.getPath());
        assertThat(issued.expiresAt()).isBetween(before.plusSeconds(299), Instant.now().plusSeconds(301));
    }

    @Test
    @DisplayName("발급한 공개 주소는 본인 경로 접두어로 시작하고, 이름은 매번 다르다")
    void ownPrefixAndUniqueNames() {
        String first = storage.issueUploadUrl(ReviewImageUpload.of("image/jpeg", 1L, 10L), 13L).imageUrl();
        String second = storage.issueUploadUrl(ReviewImageUpload.of("image/jpeg", 1L, 10L), 13L).imageUrl();

        assertThat(storage.ownImageUrlPrefix(13L))
            .isEqualTo("https://" + BUCKET + ".s3." + REGION + ".amazonaws.com/dev/reviews/13/");
        assertThat(first).startsWith(storage.ownImageUrlPrefix(13L)).endsWith(".jpg").isNotEqualTo(second);
        assertThat(ReviewImageUpload.isGeneratedName(first.substring(storage.ownImageUrlPrefix(13L).length())))
            .isTrue();
    }

    @Test
    @DisplayName("업로드 확인: 공개 주소를 key로 바꿔 한 장씩 확인하고 없는 주소만 돌려준다")
    void findsNotUploaded() {
        String prefix = storage.ownImageUrlPrefix(13L);
        String present = prefix + "0b6f3c1e-8a2d-4c5b-9e7f-000000000001.jpg";
        String absent = prefix + "0b6f3c1e-8a2d-4c5b-9e7f-000000000002.png";
        presence.put("dev/reviews/13/0b6f3c1e-8a2d-4c5b-9e7f-000000000002.png", Presence.ABSENT);

        assertThat(storage.findNotUploaded(List.of(present, absent))).containsExactly(absent);
        assertThat(checkedKeys).containsExactly("dev/reviews/13/0b6f3c1e-8a2d-4c5b-9e7f-000000000001.jpg",
            "dev/reviews/13/0b6f3c1e-8a2d-4c5b-9e7f-000000000002.png");
        assertThat(storage.findNotUploaded(List.of())).isEmpty();
    }

    @Test
    @DisplayName("업로드 확인: 확인 불가가 하나라도 있으면 CheckUnavailableException, 우리 버킷 주소가 아니면 없음")
    void unavailableOrForeign() {
        String prefix = storage.ownImageUrlPrefix(13L);
        presence.put("dev/reviews/13/0b6f3c1e-8a2d-4c5b-9e7f-000000000003.jpg", Presence.UNKNOWN);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.findNotUploaded(
                List.of(prefix + "0b6f3c1e-8a2d-4c5b-9e7f-000000000003.jpg")))
            .isInstanceOf(ReviewImageStorage.CheckUnavailableException.class)
            .hasMessageNotContaining("0b6f3c1e");
        assertThat(storage.findNotUploaded(List.of("https://evil.example.com/x.jpg")))
            .containsExactly("https://evil.example.com/x.jpg");
    }
}
