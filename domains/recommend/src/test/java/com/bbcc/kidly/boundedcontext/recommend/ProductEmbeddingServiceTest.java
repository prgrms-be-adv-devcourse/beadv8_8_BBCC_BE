package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.bbcc.kidly.boundedcontext.recommend.app.embedding.ProductEmbeddingService;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbedder;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingStore;
import com.bbcc.kidly.boundedcontext.recommend.app.port.embedding.ProductEmbeddingTarget;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductEmbeddingServiceTest {

    private static final Long PRODUCT_1_ID = 1L;
    private static final Long PRODUCT_2_ID = 2L;
    private static final Long PRODUCT_3_ID = 3L;
    private static final String PRODUCT_1_TEXT = "상품명: 데일리 맨투맨";
    private static final String PRODUCT_2_TEXT = "상품명: 데일리 후드티";
    private static final String PRODUCT_3_TEXT = "상품명: 데일리 조거팬츠";

    @Mock
    private ProductEmbeddingStore store;

    @Mock
    private ProductEmbedder embedder;

    private ProductEmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new ProductEmbeddingService(store, embedder);
    }

    @Test
    @DisplayName("처음 보내는 상품들은 시도 시각을 남긴 뒤 한 번의 요청으로 임베딩하고, 받은 벡터를 각 상품의 글과 함께 저장한다")
    void embedNeverAttemptedProductsInOneRequest() {
        EmbeddingVector vector1 = vectorOf(0.1f);
        EmbeddingVector vector2 = vectorOf(0.2f);
        given(store.findStale(anyInt())).willReturn(List.of(
            new ProductEmbeddingTarget(PRODUCT_1_ID, PRODUCT_1_TEXT, false),
            new ProductEmbeddingTarget(PRODUCT_2_ID, PRODUCT_2_TEXT, false)));
        given(embedder.embed(List.of(PRODUCT_1_TEXT, PRODUCT_2_TEXT))).willReturn(List.of(vector1, vector2));

        service.embedStaleProducts();

        InOrder inOrder = inOrder(store, embedder);
        inOrder.verify(store).markAttempted(List.of(PRODUCT_1_ID, PRODUCT_2_ID));
        inOrder.verify(embedder).embed(List.of(PRODUCT_1_TEXT, PRODUCT_2_TEXT));
        inOrder.verify(store).saveIfTextUnchanged(PRODUCT_1_ID, PRODUCT_1_TEXT, vector1);
        inOrder.verify(store).saveIfTextUnchanged(PRODUCT_2_ID, PRODUCT_2_TEXT, vector2);
    }

    @Test
    @DisplayName("낡은 상품이 없으면 임베딩 요청을 보내지 않는다")
    void doNotRequestWhenNothingIsStale() {
        given(store.findStale(anyInt())).willReturn(List.of());

        service.embedStaleProducts();

        verify(store).findStale(anyInt());
        verifyNoMoreInteractions(store);
        verifyNoInteractions(embedder);
    }

    @Test
    @DisplayName("앞서 실패한 상품은 처음 보내는 상품들 뒤에 하나씩 따로 요청한다")
    void embedRetriedProductsOneByOneAfterNeverAttemptedOnes() {
        EmbeddingVector vector1 = vectorOf(0.1f);
        EmbeddingVector vector2 = vectorOf(0.2f);
        EmbeddingVector vector3 = vectorOf(0.3f);
        given(store.findStale(anyInt())).willReturn(List.of(
            new ProductEmbeddingTarget(PRODUCT_3_ID, PRODUCT_3_TEXT, false),
            new ProductEmbeddingTarget(PRODUCT_1_ID, PRODUCT_1_TEXT, true),
            new ProductEmbeddingTarget(PRODUCT_2_ID, PRODUCT_2_TEXT, true)));
        given(embedder.embed(List.of(PRODUCT_3_TEXT))).willReturn(List.of(vector3));
        given(embedder.embed(List.of(PRODUCT_1_TEXT))).willReturn(List.of(vector1));
        given(embedder.embed(List.of(PRODUCT_2_TEXT))).willReturn(List.of(vector2));

        service.embedStaleProducts();

        InOrder inOrder = inOrder(store, embedder);
        inOrder.verify(store).markAttempted(List.of(PRODUCT_3_ID));
        inOrder.verify(embedder).embed(List.of(PRODUCT_3_TEXT));
        inOrder.verify(store).saveIfTextUnchanged(PRODUCT_3_ID, PRODUCT_3_TEXT, vector3);
        inOrder.verify(store).markAttempted(List.of(PRODUCT_1_ID));
        inOrder.verify(embedder).embed(List.of(PRODUCT_1_TEXT));
        inOrder.verify(store).saveIfTextUnchanged(PRODUCT_1_ID, PRODUCT_1_TEXT, vector1);
        inOrder.verify(store).markAttempted(List.of(PRODUCT_2_ID));
        inOrder.verify(embedder).embed(List.of(PRODUCT_2_TEXT));
        inOrder.verify(store).saveIfTextUnchanged(PRODUCT_2_ID, PRODUCT_2_TEXT, vector2);
        verifyNoMoreInteractions(embedder);
    }

    @Test
    @DisplayName("처음 보내는 상품들의 요청이 실패하면 아무것도 저장하지 않고, 앞서 실패한 상품도 보내지 않으며, 예외를 던지지 않는다")
    void stopWithoutSavingWhenNeverAttemptedRequestFails() {
        given(store.findStale(anyInt())).willReturn(List.of(
            new ProductEmbeddingTarget(PRODUCT_3_ID, PRODUCT_3_TEXT, false),
            new ProductEmbeddingTarget(PRODUCT_1_ID, PRODUCT_1_TEXT, true)));
        given(embedder.embed(List.of(PRODUCT_3_TEXT))).willThrow(new IllegalStateException("임베딩 요청 실패"));

        assertThatCode(service::embedStaleProducts).doesNotThrowAnyException();

        verify(store).findStale(anyInt());
        verify(store).markAttempted(List.of(PRODUCT_3_ID));
        verifyNoMoreInteractions(store);
        verify(embedder).embed(List.of(PRODUCT_3_TEXT));
        verifyNoMoreInteractions(embedder);
    }

    private static EmbeddingVector vectorOf(float value) {
        float[] values = new float[EmbeddingVector.DIMENSIONS];
        Arrays.fill(values, value);
        return new EmbeddingVector(values);
    }
}
