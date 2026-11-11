package com.bbcc.kidly.boundedcontext.recommend;

import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.ProductEmbeddingText;
import com.bbcc.kidly.boundedcontext.recommend.domain.purchase.PurchaseRecord;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaChild;
import com.bbcc.kidly.boundedcontext.recommend.domain.replica.RecommendReplicaProduct;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.BmiForAgeRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ChildSizeInput;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.GrowthChart;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.InfantWeightForAgeRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.InfantWeightForLengthRow;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.ProductSizes;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRange;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.SizeRecommender;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.order.event.OrderItemPurchaseConfirmedEvent;
import com.bbcc.kidly.shared.product.domain.OptionSaleStatus;
import com.bbcc.kidly.shared.product.domain.ProductStatus;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecommendFixture {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    /**
     * 기획서 7.1 자녀 1의 생년월일. 2026-10-12 기준 만 7세라 3~18세 표를 쓴다.
     */
    public static final LocalDate CHILD_1_BIRTH_DATE = LocalDate.of(2019, 1, 1);

    /**
     * 기획서 7.1 자녀 1의 ID. 회원 ID와 값이 같으면 두 칸을 바꿔 넣어도 테스트가 통과하므로 다른 값을 쓴다.
     */
    public static final Long CHILD_1_ID = 11L;

    public static final Long MEMBER_1_ID = 1L;

    public static final Long OTHER_MEMBER_ID = 2L;

    /**
     * 자녀 정보 변경 이벤트가 일어난 시각. 늦고 이른 이벤트는 이 시각에서 분을 더하거나 빼서 만든다.
     */
    public static final LocalDateTime CHILD_CHANGED_AT = LocalDateTime.of(2026, 10, 15, 10, 0);

    /**
     * 아래 네 상수는 상품 A 복사본의 판매자 · 카테고리 · 썸네일 · 등록 시각이다. ID는 상품 ID와 다른 값을 써서 두 칸을 바꿔 넣으면 테스트가 실패하게 한다.
     */
    public static final Long PRODUCT_A_SELLER_ID = 51L;

    public static final Long PRODUCT_A_CATEGORY_ID = 52L;

    public static final String PRODUCT_A_THUMBNAIL_URL = "https://cdn.kidly.test/products/31.jpg";

    public static final LocalDateTime PRODUCT_A_REGISTERED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);

    public static final String PRODUCT_A_NAME = "데일리 맨투맨";

    public static final String PRODUCT_A_BRAND = "키들리";

    public static final String PRODUCT_A_DESCRIPTION = "상품 설명";

    /**
     * 상품 변경 이벤트가 일어난 시각. 늦고 이른 이벤트는 이 시각에서 분을 더하거나 빼서 만든다.
     */
    public static final LocalDateTime PRODUCT_CHANGED_AT = LocalDateTime.of(2026, 10, 15, 9, 0);

    /**
     * 아래 네 상수는 구매확정에 쓰는 주문 · 주문상품 · 상품 · 옵션 ID다. ID마다 다른 값을 써서 두 칸을 바꿔 넣으면 테스트가 실패하게 한다.
     */
    public static final Long ORDER_ID = 100L;

    public static final Long ORDER_ITEM_ID = 101L;

    public static final Long PRODUCT_A_ID = 31L;

    public static final Long PRODUCT_A_120_OPTION_ID = 32L;

    public static final LocalDateTime PURCHASE_CONFIRMED_AT = LocalDateTime.of(2026, 10, 15, 12, 0);

    /**
     * 구매확정 이벤트에만 있고 구매 기록에는 옮기지 않는 판매자 ID다. 다른 ID와 겹치지 않는 값을 쓴다.
     */
    public static final Long SELLER_ID = 41L;

    private RecommendFixture() {
    }

    /**
     * 회원 1이 자녀 1을 골라 주문한 상품 A의 120 옵션을 구매확정했다.
     */
    public static PurchaseRecord.RecordInfo purchaseConfirmation() {
        return purchaseConfirmationFor(CHILD_1_ID);
    }

    /**
     * {@link #purchaseConfirmation()}과 같은 구매확정에서 자녀만 바꾼다. 자녀를 고르지 않았으면 null이다.
     */
    public static PurchaseRecord.RecordInfo purchaseConfirmationFor(Long childProfileId) {
        return new PurchaseRecord.RecordInfo(ORDER_ITEM_ID, ORDER_ID, MEMBER_1_ID, childProfileId, PRODUCT_A_ID,
            PRODUCT_A_120_OPTION_ID, "120", PURCHASE_CONFIRMED_AT);
    }

    /**
     * 주문 도메인이 보내는 구매확정 이벤트를 만든다. 값은 {@link #purchaseConfirmation()}과 같고, 수량 2개 · 금액 50,000원이다.
     * 이벤트 발생 시각은 구매확정 시각보다 1초 늦게 두어, 두 시각을 바꿔 넣으면 테스트가 실패하게 한다.
     */
    public static OrderItemPurchaseConfirmedEvent purchaseConfirmedEvent() {
        return purchaseConfirmedEventFor(CHILD_1_ID);
    }

    /**
     * {@link #purchaseConfirmedEvent()}와 같은 구매확정 이벤트에서 자녀만 바꾼다. 자녀를 고르지 않았으면 null이다.
     */
    public static OrderItemPurchaseConfirmedEvent purchaseConfirmedEventFor(Long childId) {
        return new OrderItemPurchaseConfirmedEvent(UUID.randomUUID(), PURCHASE_CONFIRMED_AT.plusSeconds(1),
            new OrderItemPurchaseConfirmedEvent.Data(ORDER_ITEM_ID, ORDER_ID, MEMBER_1_ID, SELLER_ID, childId,
                PRODUCT_A_ID, PRODUCT_A_120_OPTION_ID, "120", 2, 50_000L, PURCHASE_CONFIRMED_AT));
    }

    /**
     * 기획서 7.1 자녀 1의 자녀 정보 변경: 회원 1의 자녀, 2019-01-01생 남자, 키 115.0cm, 몸무게 20.0kg, 체형 보통, 선호 핏 여유.
     */
    public static RecommendReplicaChild.ChangeInfo childChange() {
        return childChange(BodyType.NORMAL, PreferredFit.LOOSE);
    }

    public static RecommendReplicaChild.ChangeInfo childChange(BodyType bodyType, PreferredFit preferredFit) {
        return new RecommendReplicaChild.ChangeInfo(CHILD_1_ID, MEMBER_1_ID, new BigDecimal("115.0"),
            new BigDecimal("20.0"), Gender.MALE, CHILD_1_BIRTH_DATE, bodyType, preferredFit, false, CHILD_CHANGED_AT);
    }

    /**
     * 자녀 1의 키만 바꾼 자녀 정보 변경. 어느 이벤트가 반영됐는지 키로 구분한다.
     */
    public static RecommendReplicaChild.ChangeInfo childChangeAt(BigDecimal heightCm, LocalDateTime occurredAt) {
        return new RecommendReplicaChild.ChangeInfo(CHILD_1_ID, MEMBER_1_ID, heightCm, new BigDecimal("20.0"),
            Gender.MALE, CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, false, occurredAt);
    }

    /**
     * 자녀 1의 삭제. 삭제 이벤트도 삭제 직전 값(키 115.0cm 등)을 그대로 담는다.
     */
    public static RecommendReplicaChild.ChangeInfo childDeleted(LocalDateTime occurredAt) {
        return new RecommendReplicaChild.ChangeInfo(CHILD_1_ID, MEMBER_1_ID, new BigDecimal("115.0"),
            new BigDecimal("20.0"), Gender.MALE, CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, true,
            occurredAt);
    }

    /**
     * 자녀 프로필 도메인이 보내는 자녀 1의 자녀 정보 변경됨 이벤트. 값은 {@link #childChange()}와 같다.
     */
    public static ChildChangedEvent childChangedEvent() {
        return childChangedEventAt(new BigDecimal("115.0"), CHILD_CHANGED_AT);
    }

    /**
     * 자녀 1의 키와 발생 시각만 바꾼 자녀 정보 변경됨 이벤트. 어느 이벤트가 반영됐는지 키로 구분한다.
     */
    public static ChildChangedEvent childChangedEventAt(BigDecimal heightCm, LocalDateTime occurredAt) {
        return childChangedEventOf(heightCm, false, occurredAt);
    }

    /**
     * 자녀 1을 삭제한 자녀 정보 변경됨 이벤트. 삭제 직전 값(키 115.0cm 등)을 그대로 담는다.
     */
    public static ChildChangedEvent childDeletedEvent(LocalDateTime occurredAt) {
        return childChangedEventOf(new BigDecimal("115.0"), true, occurredAt);
    }

    private static ChildChangedEvent childChangedEventOf(BigDecimal heightCm, boolean deleted,
        LocalDateTime occurredAt) {
        return new ChildChangedEvent(UUID.randomUUID(), occurredAt, new ChildChangedEvent.Data(CHILD_1_ID,
            MEMBER_1_ID, heightCm, new BigDecimal("20.0"), Gender.MALE, CHILD_1_BIRTH_DATE, BodyType.NORMAL,
            PreferredFit.LOOSE, deleted));
    }

    public static SizeRecommender recommender() {
        return new SizeRecommender(growthChart());
    }

    /**
     * 테스트용 체형 기준표. 질병관리청 2017 성장도표에서 남자 행 몇 개만 옮겼다.
     * 연령별 체질량지수 5백분위 · 85백분위는 24개월 14.2 · 17.4, 93개월 14.1 · 18.9, 227개월 18.6 · 25.4다.
     * 자녀 1은 TODAY에 93개월이라, 키 115cm면 18.6kg까지 마름, 25.0kg부터 통통으로 판정된다.
     * 영아 표는 23개월 연령별 체중 5백분위(9.9kg)와 누운키 80.0cm의 신장별 체중 95백분위(12.0kg)만 있다.
     * 그래서 24개월 미만은 누운키가 80.0cm일 때만 판정하고, 그 밖의 키는 판정하지 않고 입력 체형을 쓴다.
     */
    public static GrowthChart growthChart() {
        return GrowthChart.of(
            List.of(
                new BmiForAgeRow(Gender.MALE, 24, 14.2, 17.4),
                new BmiForAgeRow(Gender.MALE, 93, 14.1, 18.9),
                new BmiForAgeRow(Gender.MALE, 227, 18.6, 25.4)),
            List.of(new InfantWeightForAgeRow(Gender.MALE, 23, 9.9)),
            List.of(new InfantWeightForLengthRow(Gender.MALE, 80.0, 12.0)));
    }

    /**
     * 기획서 7.1 자녀 1: 2019-01-01생 남자, 키 115.0cm, 몸무게 20.0kg, 체형 보통, 선호 핏 여유.
     */
    public static ChildSizeInput childSizeInput() {
        return childSizeInputWithHeight(115.0);
    }

    public static ChildSizeInput childSizeInputWith(
        double heightCm, double weightKg, BodyType bodyType, PreferredFit preferredFit) {
        return ChildSizeInput.of(heightCm, weightKg, Gender.MALE, CHILD_1_BIRTH_DATE, bodyType, preferredFit);
    }

    public static ChildSizeInput childSizeInputBornOn(
        LocalDate birthDate, double weightKg, PreferredFit preferredFit) {
        return ChildSizeInput.of(115.0, weightKg, Gender.MALE, birthDate, BodyType.NORMAL, preferredFit);
    }

    /**
     * 키만 바꾼 자녀 1. 몸무게는 자녀 1(115cm, 20kg)과 체질량지수가 같도록 키에 맞춘다. 어느 키에서도 체형이 보통으로 판정되게 하려는 것이다.
     */
    public static ChildSizeInput childSizeInputWithHeight(double heightCm) {
        double heightRatio = heightCm / 115.0;
        return ChildSizeInput.of(heightCm, 20.0 * heightRatio * heightRatio, Gender.MALE,
                CHILD_1_BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE);
    }

    /**
     * 기획서 7.1 상품 A: 110(권장 키 105~115cm), 120(115~125cm).
     */
    public static ProductSizes productA() {
        return ProductSizes.of(false, List.of(
            SizeRange.of("110", 105, 115),
            SizeRange.of("120", 115, 125)));
    }

    /**
     * 상품 A의 사이즈별 실측: 110(권장 키 105~115cm), 120(115~125cm)에 총장과 가슴단면이 있다.
     */
    public static List<RecommendReplicaProduct.SizeSpecInfo> productASizeSpecInfos() {
        return List.of(
            new RecommendReplicaProduct.SizeSpecInfo("110", 105, 115,
                Map.of("TOTAL_LENGTH", new BigDecimal("42"), "CHEST_WIDTH", new BigDecimal("34"))),
            new RecommendReplicaProduct.SizeSpecInfo("120", 115, 125,
                Map.of("TOTAL_LENGTH", new BigDecimal("45"), "CHEST_WIDTH", new BigDecimal("36"))));
    }

    public static List<ProductEmbeddingText.InfoValue> productAInfoValues() {
        return List.of(
            new ProductEmbeddingText.InfoValue("season", "fall"),
            new ProductEmbeddingText.InfoValue("material", "cotton"),
            new ProductEmbeddingText.InfoValue("season", "spring"));
    }

    /**
     * 판매 중인 상품 A의 상품 변경: 110(권장 키 105~115cm), 120(115~125cm) 옵션이 있다.
     */
    public static RecommendReplicaProduct.ChangeInfo productAChange() {
        return productAChangeOf(false, List.of(
            productOptionInfo("110", 105, 115),
            productOptionInfo("120", 115, 125)), PRODUCT_CHANGED_AT);
    }

    /**
     * {@link #productAChange()}로 만든 상품 A의 복사본이다. 110과 120 옵션 모두 판매할 수 있다.
     */
    public static RecommendReplicaProduct productAReplica() {
        return RecommendReplicaProduct.create(productAChange());
    }

    /**
     * {@link #productAReplica()}와 같은 상품 A의 복사본에서 옵션만 바꾼다.
     */
    public static RecommendReplicaProduct productAReplicaWith(List<RecommendReplicaProduct.OptionInfo> options) {
        return RecommendReplicaProduct.create(productAChangeOf(false, options, PRODUCT_CHANGED_AT));
    }

    /**
     * 사이즈 무관 상품으로 바꾼 상품 A의 복사본이다. 권장 키가 없는 FREE 옵션 하나만 있고, 판매가 25,000원에 판매할 수 있다.
     */
    public static RecommendReplicaProduct productASizeFreeReplica() {
        return productASizeFreeReplica(false, OptionSaleStatus.ON_SALE);
    }

    /**
     * {@link #productASizeFreeReplica()}에서 FREE 옵션의 품절 여부와 옵션 판매 상태만 바꾼다.
     */
    public static RecommendReplicaProduct productASizeFreeReplica(boolean soldOut, OptionSaleStatus saleStatus) {
        return RecommendReplicaProduct.create(productAChangeOf(true,
            List.of(new RecommendReplicaProduct.OptionInfo("FREE", null, null, 25_000L, soldOut, saleStatus)),
            PRODUCT_CHANGED_AT));
    }

    /**
     * {@link #productAReplica()}와 같은 상품 A의 복사본에서 상품 ID와 등록 시각만 바꾼다. 목록의 정렬 순서를 확인할 때 쓴다.
     */
    public static RecommendReplicaProduct productReplicaRegisteredAt(Long productId, LocalDateTime registeredAt) {
        return productReplicaRegisteredAt(productId, PRODUCT_A_SELLER_ID, registeredAt);
    }

    /**
     * {@link #productReplicaRegisteredAt(Long, LocalDateTime)}에서 판매자 ID도 바꾼다. 목록에 상호명을 붙이는지 확인할 때 쓴다.
     */
    public static RecommendReplicaProduct productReplicaRegisteredAt(
        Long productId, Long sellerId, LocalDateTime registeredAt) {
        return RecommendReplicaProduct.create(new RecommendReplicaProduct.ChangeInfo(productId,
            sellerId, PRODUCT_A_CATEGORY_ID, PRODUCT_A_NAME, ProductStatus.ON_SALE,
            PRODUCT_A_THUMBNAIL_URL, registeredAt, false, productAChange().options(), PRODUCT_A_BRAND,
            PRODUCT_A_DESCRIPTION, productASizeSpecInfos(), productAInfoValues(), PRODUCT_CHANGED_AT));
    }

    /**
     * {@link #productAReplica()}와 같은 상품 A의 복사본에서 상품 판매 상태만 바꾼다. 110과 120 옵션은 그대로 판매할 수 있다.
     */
    public static RecommendReplicaProduct productAReplicaWithStatus(ProductStatus status) {
        return RecommendReplicaProduct.create(new RecommendReplicaProduct.ChangeInfo(PRODUCT_A_ID,
            PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID, PRODUCT_A_NAME, status, PRODUCT_A_THUMBNAIL_URL,
            PRODUCT_A_REGISTERED_AT, false, productAChange().options(), PRODUCT_A_BRAND, PRODUCT_A_DESCRIPTION,
            productASizeSpecInfos(), productAInfoValues(), PRODUCT_CHANGED_AT));
    }

    /**
     * 판매 중인 상품 A의 상품 정보 변경됨이다. {@link #productAChange()}와 같은 값을 이벤트 칸에 담는다.
     */
    public static ProductChangedEvent productAChangedEvent() {
        return productAChangedEventAt(List.of(
            productOption(1L, "110", 105, 115),
            productOption(2L, "120", 115, 125)), PRODUCT_CHANGED_AT);
    }

    /**
     * {@link #productAChangedEvent()}와 같은 상품 정보 변경됨에서 옵션과 발생 시각만 바꾼다. 이벤트 ID는 새로 만든다.
     */
    public static ProductChangedEvent productAChangedEventAt(
        List<ProductChangedEvent.Option> options, LocalDateTime occurredAt) {
        return productAChangedEventOf(false, options, occurredAt);
    }

    /**
     * {@link #productAChangedEventAt(List, LocalDateTime)}에서 사이즈 무관 여부도 바꾼다.
     */
    public static ProductChangedEvent productAChangedEventOf(
        boolean sizeFree, List<ProductChangedEvent.Option> options, LocalDateTime occurredAt) {
        return new ProductChangedEvent(UUID.randomUUID(), occurredAt, new ProductChangedEvent.Data(
            PRODUCT_A_ID, PRODUCT_A_SELLER_ID, PRODUCT_A_NAME, PRODUCT_A_CATEGORY_ID, ProductStatus.ON_SALE,
            PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, sizeFree, options));
    }

    /**
     * 상품 정보 변경됨의 옵션 하나다. 추천이 쓰지 않는 옵션 이름 · 판매가 · 품절 · 판매 상태는 정해 둔 값을 넣는다.
     */
    public static ProductChangedEvent.Option productOption(
        Long optionId, String size, Integer minHeightCm, Integer maxHeightCm) {
        return productOption(optionId, size, minHeightCm, maxHeightCm, 25_000L, false, OptionSaleStatus.ON_SALE);
    }

    /**
     * {@link #productOption(Long, String, Integer, Integer)}에서 판매가 · 품절 · 옵션 판매 상태도 바꾼다.
     */
    public static ProductChangedEvent.Option productOption(Long optionId, String size, Integer minHeightCm,
        Integer maxHeightCm, long price, boolean soldOut, OptionSaleStatus saleStatus) {
        return new ProductChangedEvent.Option(optionId, "색상: 네이비 / 사이즈: " + size, size, price,
            minHeightCm, maxHeightCm, soldOut, saleStatus);
    }

    /**
     * {@link #productAChange()}와 같은 상품 변경에서 옵션만 바꾼다. 판매가마다 옵션을 하나씩 만들고 사이즈는 110부터 10씩 올린다.
     * 판매가를 넘기지 않으면 옵션이 없는 상품 변경이 된다.
     */
    public static RecommendReplicaProduct.ChangeInfo productAChangeWithPrices(long... prices) {
        List<RecommendReplicaProduct.OptionInfo> options = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            int size = 110 + 10 * i;
            options.add(productOptionInfo(String.valueOf(size), size - 5, size + 5, prices[i]));
        }
        return productAChangeOf(false, options, PRODUCT_CHANGED_AT);
    }

    /**
     * {@link #productAChange()}와 같은 상품 변경에서 사이즈 무관 여부, 옵션, 발생 시각만 바꾼다.
     */
    /**
     * 카테고리 경로가 「의류 > 상의」인 상품 A의 복사본이다. 상의 기준 옷과 비교할 때 쓴다.
     */
    public static RecommendReplicaProduct productATopReplica() {
        return RecommendReplicaProduct.create(new RecommendReplicaProduct.ChangeInfo(PRODUCT_A_ID,
            PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID, PRODUCT_A_NAME, ProductStatus.ON_SALE,
            PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false, productAChange().options(), PRODUCT_A_BRAND,
            PRODUCT_A_DESCRIPTION, productASizeSpecInfos(), productAInfoValues(), PRODUCT_CHANGED_AT,
            List.of(new RecommendReplicaProduct.CategoryPathEntryInfo(1L, "의류"),
                new RecommendReplicaProduct.CategoryPathEntryInfo(PRODUCT_A_CATEGORY_ID, "상의"))));
    }

    public static RecommendReplicaProduct.ChangeInfo productAChangeOf(boolean sizeFree,
        List<RecommendReplicaProduct.OptionInfo> options, LocalDateTime occurredAt) {
        return new RecommendReplicaProduct.ChangeInfo(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID,
            PRODUCT_A_NAME, ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, sizeFree,
            options, PRODUCT_A_BRAND, PRODUCT_A_DESCRIPTION, productASizeSpecInfos(), productAInfoValues(),
            occurredAt);
    }

    /**
     * {@link #productAChange()}와 같은 값으로 만든 상품 복사본에서 상품 ID와 판매 상태만 바꾼다.
     * 여러 상품 복사본을 저장해 조회 조건을 확인할 때 쓴다.
     */
    public static RecommendReplicaProduct replicaProduct(Long productId, ProductStatus status) {
        return replicaProduct(productId, status, PRODUCT_A_CATEGORY_ID);
    }

    /**
     * {@link #replicaProduct(Long, ProductStatus)}에서 카테고리도 바꾼다. 카테고리 조건으로 읽는지 확인할 때 쓴다.
     */
    public static RecommendReplicaProduct replicaProduct(Long productId, ProductStatus status, Long categoryId) {
        return RecommendReplicaProduct.create(new RecommendReplicaProduct.ChangeInfo(productId, PRODUCT_A_SELLER_ID,
            categoryId, PRODUCT_A_NAME, status, PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false,
            List.of(productOptionInfo("110", 105, 115), productOptionInfo("120", 115, 125)), PRODUCT_A_BRAND,
            PRODUCT_A_DESCRIPTION, productASizeSpecInfos(), productAInfoValues(), PRODUCT_CHANGED_AT));
    }

    /**
     * {@link #productAChange()}와 같은 상품 변경에서 상품명, 옵션, 발생 시각만 바꾼다.
     */
    public static RecommendReplicaProduct.ChangeInfo productAChangeNamed(String name,
        List<RecommendReplicaProduct.OptionInfo> options, LocalDateTime occurredAt) {
        return new RecommendReplicaProduct.ChangeInfo(PRODUCT_A_ID, PRODUCT_A_SELLER_ID, PRODUCT_A_CATEGORY_ID,
            name, ProductStatus.ON_SALE, PRODUCT_A_THUMBNAIL_URL, PRODUCT_A_REGISTERED_AT, false, options,
            PRODUCT_A_BRAND, PRODUCT_A_DESCRIPTION, productASizeSpecInfos(), productAInfoValues(), occurredAt);
    }

    /**
     * 상품 복사본에 넣을 옵션 하나다. 판매가 · 품절 · 판매 상태는 판매할 수 있는 옵션의 값으로 정해 둔다.
     */
    public static RecommendReplicaProduct.OptionInfo productOptionInfo(
        String size, Integer minHeightCm, Integer maxHeightCm) {
        return productOptionInfo(size, minHeightCm, maxHeightCm, 25_000L);
    }

    /**
     * {@link #productOptionInfo(String, Integer, Integer)}에서 판매가만 바꾼다.
     */
    public static RecommendReplicaProduct.OptionInfo productOptionInfo(
        String size, Integer minHeightCm, Integer maxHeightCm, long price) {
        return new RecommendReplicaProduct.OptionInfo(size, minHeightCm, maxHeightCm, price, false,
            OptionSaleStatus.ON_SALE);
    }

    /**
     * {@link #productOptionInfo(String, Integer, Integer)}에서 품절 여부와 옵션 판매 상태만 바꾼다. 권장 키는 105 ~ 115cm로 둔다.
     */
    public static RecommendReplicaProduct.OptionInfo productOptionInfo(
        String size, boolean soldOut, OptionSaleStatus saleStatus) {
        return new RecommendReplicaProduct.OptionInfo(size, 105, 115, 25_000L, soldOut, saleStatus);
    }
}
