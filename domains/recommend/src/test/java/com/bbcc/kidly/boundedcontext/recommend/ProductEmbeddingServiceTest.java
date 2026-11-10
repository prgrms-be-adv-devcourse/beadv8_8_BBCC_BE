package com.bbcc.kidly.boundedcontext.recommend;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

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
    private static final String PRODUCT_1_TEXT = "상품명: 데일리 맨투맨";
    private static final String PRODUCT_2_TEXT = "상품명: 데일리 후드티";

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

    private static EmbeddingVector vectorOf(float value) {
        float[] values = new float[EmbeddingVector.DIMENSIONS];
        Arrays.fill(values, value);
        return new EmbeddingVector(values);
    }
}
