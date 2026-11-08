package com.bbcc.kidly.boundedcontext.review.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.Deletion;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.StoredPage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 정리용 저장소는 이 환경 리뷰 경로의 정리할 수 있는 key만 저장소에 넘긴다(다른 환경 · 다른 기능 · 모양이 틀린 key는 부르지도 않는다).
 */
class S3ReviewImageCleanupStorageTest {

    private static final String BASE = "https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/";
    private static final String NAME = "0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.jpg";
    private static final String KEY = "dev/reviews/13/" + NAME;

    private final RecordingStore store = new RecordingStore();
    private final S3ReviewImageCleanupStorage storage = new S3ReviewImageCleanupStorage(store, "dev", BASE);

    @Test
    @DisplayName("이 환경 리뷰 사진 공개 주소면 key로 바꾸고, key는 다시 같은 공개 주소가 된다")
    void keyOfAndBack() {
        assertThat(storage.keyOf(BASE + KEY)).contains(KEY);
        assertThat(storage.imageUrlOf(KEY)).isEqualTo(BASE + KEY);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {
        BASE + "prod/reviews/13/" + NAME,
        BASE + "dev/products/13/" + NAME,
        BASE + "dev/reviews/0/" + NAME,
        "https://other-bucket.s3.ap-northeast-2.amazonaws.com/dev/reviews/13/" + NAME,
        "http://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/dev/reviews/13/" + NAME,
        BASE + "dev/reviews/13/../14/" + NAME,
        "dev/reviews/13/" + NAME,
        ""})
    @DisplayName("다른 버킷 · 다른 환경 · 다른 기능 · 모양이 틀린 주소는 key가 없다(정리 대기에 넣지 않는다)")
    void noKeyForForeignUrl(String url) {
        assertThat(storage.keyOf(url)).isEmpty();
    }

    @Test
    @DisplayName("정리할 수 있는 key만 저장소에서 지운다")
    void deletesCleanableKey() {
        assertThat(storage.isCleanable(KEY)).isTrue();
        assertThat(storage.delete(KEY)).isEqualTo(Deletion.deleted());
        assertThat(store.deleted).containsExactly(KEY);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"prod/reviews/13/" + NAME, "dev/products/13/" + NAME, "dev/reviews/13/x.jpg",
        "dev/reviews/13/" + NAME + "/", "dev/reviews/-1/" + NAME})
    @DisplayName("모양이 틀린 key는 저장소를 부르지 않고 REFUSED")
    void refusesOtherKeys(String key) {
        assertThat(storage.isCleanable(key)).isFalse();
        assertThat(storage.delete(key)).isEqualTo(Deletion.refused());
        assertThat(store.deleted).isEmpty();
    }

    @Test
    @DisplayName("목록은 이 환경 리뷰 경로 접두어로만 읽는다")
    void listsOwnPrefixOnly() {
        storage.list("token-1", 500);

        assertThat(store.listed).containsExactly("dev/reviews/|token-1|500");
    }

    @Test
    @DisplayName("저장소에 닿는지는 고른 저장소를 따른다(none이면 정리하지 않음)")
    void availability() {
        assertThat(storage.available()).isTrue();
        assertThat(new S3ReviewImageCleanupStorage(new NoReviewImageObjectStore(), "dev", BASE).available())
            .isFalse();
    }

    private static final class RecordingStore implements ReviewImageObjectStore {

        private final List<String> deleted = new ArrayList<>();
        private final List<String> listed = new ArrayList<>();

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public Deletion delete(String key) {
            deleted.add(key);
            return Deletion.deleted();
        }

        @Override
        public StoredPage list(String prefix, String continuationToken, int pageSize) {
            listed.add(prefix + "|" + continuationToken + "|" + pageSize);
            return new StoredPage(List.of(), null);
        }
    }
}
