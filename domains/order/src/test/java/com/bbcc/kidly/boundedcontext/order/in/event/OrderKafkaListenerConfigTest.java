package com.bbcc.kidly.boundedcontext.order.in.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.order.app.OrderErrorCode;
import com.bbcc.kidly.global.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * 주문 Kafka 수신 설정(#541): 스위치 · 한 건씩 완료 · earliest · 재시도 없는 예외 · DLT 대상 파티션 · DLT 로그.
 * 팩토리는 Boot configurer가 앱 설정을 넣은 뒤 주문 값만 덮어쓴다. configurer는 앱 설정 대신 대역으로 흉내 낸다.
 */
@ExtendWith(OutputCaptureExtension.class)
class OrderKafkaListenerConfigTest {

    private static final String BODY_MARKER = "본문-표식-주소";
    private static final BiPredicate<ConsumerRecord<?, ?>, Exception> NOT_KEPT = (record, e) -> false;

    private final ConsumerFactory<Object, Object> consumerFactory = mock();
    private final CommonErrorHandler errorHandler = mock();
    private final CommonErrorHandler deadLetterErrorHandler = mock();

    @ParameterizedTest(name = "앱 auto-startup={0}")
    @CsvSource({"true", "false"})
    @DisplayName("스위치가 꺼져 있으면 앱 설정과 상관없이 컨테이너를 자동 시작하지 않는다")
    void switchOffStopsAutoStartup(boolean appAutoStartup) {
        var container = containerOf(appAutoStartup, false);

        assertThat(container.isAutoStartup()).isFalse();
    }

    @ParameterizedTest(name = "앱 auto-startup={0}")
    @CsvSource({"true", "false"})
    @DisplayName("스위치가 켜져 있으면 앱 설정(테스트는 false)을 따른다")
    void switchOnFollowsApp(boolean appAutoStartup) {
        var container = containerOf(appAutoStartup, true);

        assertThat(container.isAutoStartup()).isEqualTo(appAutoStartup);
    }

    @Test
    @DisplayName("한 건씩 처리 완료를 표시하고, 처음 읽는 그룹은 처음부터(earliest) 읽으며, 주문 에러 처리기를 쓴다")
    void recordAckEarliestAndErrorHandler() {
        var container = containerOf(true, true);

        assertThat(container.getContainerProperties().getAckMode()).isEqualTo(AckMode.RECORD);
        assertThat(container.getContainerProperties().getKafkaConsumerProperties()
            .getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
        assertThat(container.getCommonErrorHandler()).isSameAs(errorHandler);
    }

    @ParameterizedTest(name = "{0} 파티션 {1}")
    @CsvSource({
        "kidly.product.events, 0, kidly.product.events.order.dlt",
        "kidly.seller.events, 1, kidly.seller.events.order.dlt",
        "kidly.childprofile.events, 2, kidly.childprofile.events.order.dlt",
        "kidly.payment.events, 1, kidly.payment.events.order.dlt"
    })
    @DisplayName("DLT는 원래 토픽 이름 + .order.dlt, 파티션은 원래 번호와 상관없이 0(DLT는 파티션 1개)")
    void deadLetterPartition(String topic, int partition, String deadLetterTopic) {
        var destination = OrderKafkaListenerConfig.deadLetterPartition(record(topic, partition));

        assertThat(destination).isEqualTo(new TopicPartition(deadLetterTopic, 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notRetryable")
    @DisplayName("다시 해도 같은 예외 · 읽기 실패는 재시도 없이 바로 DLT로 보낸다")
    void notRetryableGoesToDeadLetterAtOnce(String name, Exception cause) {
        List<ConsumerRecord<?, ?>> deadLetters = new ArrayList<>();
        DefaultErrorHandler handler = OrderKafkaListenerConfig.errorHandler((record, e) -> deadLetters.add(record),
            NOT_KEPT);
        var record = record("kidly.seller.events", 2);

        boolean recovered = handler.handleOne(listenerFailure(cause), record, mock(Consumer.class),
            mock(MessageListenerContainer.class));

        assertThat(recovered).isTrue();
        assertThat(deadLetters).containsExactly(record);
    }

    static Stream<Arguments> notRetryable() {
        return Stream.of(
            Arguments.of("BusinessException", new BusinessException(OrderErrorCode.ORDER_NOT_FOUND)),
            Arguments.of("IllegalArgumentException", new IllegalArgumentException()),
            Arguments.of("IllegalStateException", new IllegalStateException()),
            Arguments.of("NullPointerException", new NullPointerException()),
            Arguments.of("모르는 enum 값(JacksonException)",
                InvalidFormatException.from(null, "모르는 값", "UNKNOWN", Enum.class)));
    }

    @Test
    @DisplayName("DLT로 보낼 메시지를 주문이 대신 보관하면(#632) DLT로 보내지 않고 처리 완료, DLT 로그도 없다")
    void keptInsteadOfDeadLetter(CapturedOutput output) {
        List<ConsumerRecord<?, ?>> deadLetters = new ArrayList<>();
        List<Exception> keptErrors = new ArrayList<>();
        DefaultErrorHandler handler = OrderKafkaListenerConfig.errorHandler((record, e) -> deadLetters.add(record),
            (record, e) -> keptErrors.add(e));
        var failure = listenerFailure(new IllegalStateException());

        boolean recovered = handler.handleOne(failure, record("kidly.payment.events", 0), mock(Consumer.class),
            mock(MessageListenerContainer.class));

        assertThat(recovered).isTrue();
        assertThat(deadLetters).isEmpty();
        assertThat(keptErrors).singleElement().isSameAs(failure);
        assertThat(output.getOut()).doesNotContain("DLT로 보냅니다");
    }

    @Test
    @DisplayName("주문이 보관하지 못하면(#632) 지금처럼 DLT로 보낸다")
    void notKeptGoesToDeadLetter() {
        List<ConsumerRecord<?, ?>> deadLetters = new ArrayList<>();
        DefaultErrorHandler handler = OrderKafkaListenerConfig.errorHandler((record, e) -> deadLetters.add(record),
            NOT_KEPT);
        var record = record("kidly.payment.events", 0);

        handler.handleOne(listenerFailure(new IllegalStateException()), record, mock(Consumer.class),
            mock(MessageListenerContainer.class));

        assertThat(deadLetters).containsExactly(record);
    }

    @Test
    @DisplayName("보관 시도가 예상 못 한 예외로 끝나도 같은 메시지를 붙잡고 있지 않고 지금처럼 DLT로 보낸다(로그는 DLT 한 줄, 본문 없음)")
    void keepErrorGoesToDeadLetter(CapturedOutput output) {
        List<ConsumerRecord<?, ?>> deadLetters = new ArrayList<>();
        DefaultErrorHandler handler = OrderKafkaListenerConfig.errorHandler((record, e) -> deadLetters.add(record),
            (record, e) -> {
                throw new IllegalStateException("보관 시도 실패: " + BODY_MARKER);
            });
        var record = record("kidly.payment.events", 0);

        boolean recovered = handler.handleOne(listenerFailure(new IllegalStateException()), record,
            mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(recovered).isTrue();
        assertThat(deadLetters).containsExactly(record);
        assertThat(output.getOut()).containsOnlyOnce("DLT로 보냅니다").doesNotContain(BODY_MARKER);
    }

    @Test
    @DisplayName("그 밖의 예외는 처음 실패에서 DLT로 보내지 않고 다시 시도한다")
    void otherExceptionIsRetried() {
        List<ConsumerRecord<?, ?>> deadLetters = new ArrayList<>();
        DefaultErrorHandler handler = OrderKafkaListenerConfig.errorHandler((record, e) -> deadLetters.add(record),
            NOT_KEPT);

        boolean recovered = handler.handleOne(listenerFailure(new RuntimeException("일시 장애")),
            record("kidly.seller.events", 0), mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(recovered).isFalse();
        assertThat(deadLetters).isEmpty();
    }

    @Test
    @DisplayName("DLT로 보낼 때 ERROR 로그에는 eventType · eventId · 키 · 예외 종류만 남고 본문 · 예외 메시지는 없다")
    void deadLetterLogHasNoBody(CapturedOutput output) {
        DefaultErrorHandler handler = OrderKafkaListenerConfig.errorHandler((record, e) -> { }, NOT_KEPT);

        handler.handleOne(listenerFailure(new IllegalArgumentException("값: " + BODY_MARKER)),
            record("kidly.seller.events", 1), mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(output.getOut())
            .contains("eventType=seller.changed", "eventId=evt-1", "key=7",
                "exception=java.lang.IllegalArgumentException")
            .doesNotContain(BODY_MARKER);
    }

    @ParameterizedTest(name = "스위치 {0}")
    @CsvSource({"true", "false"})
    @DisplayName("DLT 컨테이너(#601)는 저장 재시도 에러 처리기를 쓰고(DLT로 다시 보내지 않음) 스위치 · earliest는 다른 컨테이너와 같다")
    void deadLetterContainerUsesStoreRetry(boolean consumerEnabled) {
        var container = containerOf(true, consumerEnabled, "kidly.payment.events.order.dlt",
            "kidly.product.events.order.dlt");

        assertThat(container.getCommonErrorHandler()).isSameAs(deadLetterErrorHandler);
        assertThat(container.isAutoStartup()).isEqualTo(consumerEnabled);
        assertThat(container.getContainerProperties().getKafkaConsumerProperties()
            .getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
    }

    @Test
    @DisplayName("받기 · DLT 컨테이너 모두 로그 추적 값(MDC) 인터셉터를 쓴다(#603)")
    void logContextInterceptor() {
        assertThat(containerOf(true, true).getRecordInterceptor())
            .isInstanceOf(OrderLogContextRecordInterceptor.class);
        assertThat(containerOf(true, true, "kidly.payment.events.order.dlt").getRecordInterceptor())
            .isInstanceOf(OrderLogContextRecordInterceptor.class);
    }

    @Test
    @DisplayName("DLT 저장 재시도 간격은 1초부터 두 배씩 최대 60초, 끝없이 다시 시도한다(포기해 메시지를 건너뛰지 않음)")
    void deadLetterStoreBackOffNeverStops() {
        BackOffExecution execution = OrderKafkaListenerConfig.deadLetterStoreBackOff().start();

        List<Long> intervals = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            intervals.add(execution.nextBackOff());
        }

        assertThat(intervals.subList(0, 8)).containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L,
            60_000L, 60_000L);
        assertThat(intervals).doesNotContain(BackOffExecution.STOP);
    }

    @Test
    @DisplayName("DLT 저장 재시도 처리기는 어떤 예외에도 메시지를 처리 완료로 넘기지 않는다(재시도하지 않는 기본 예외 포함)")
    void deadLetterStoreErrorHandlerNeverRecovers() {
        DefaultErrorHandler handler = OrderKafkaListenerConfig.deadLetterStoreErrorHandler(new FixedBackOff(0L,
            FixedBackOff.UNLIMITED_ATTEMPTS));

        for (Exception cause : List.of(new RuntimeException("DB 장애"), new ClassCastException(),
            new IllegalArgumentException())) {
            boolean recovered = handler.handleOne(listenerFailure(cause), record("kidly.payment.events.order.dlt", 0),
                mock(Consumer.class), mock(MessageListenerContainer.class));
            assertThat(recovered).as(cause.getClass().getSimpleName()).isFalse();
        }
    }

    private ConcurrentMessageListenerContainer<Object, Object> containerOf(boolean appAutoStartup,
        boolean consumerEnabled) {
        return containerOf(appAutoStartup, consumerEnabled, "kidly.seller.events");
    }

    private ConcurrentMessageListenerContainer<Object, Object> containerOf(boolean appAutoStartup,
        boolean consumerEnabled, String... topics) {
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer = mock();
        // Boot configurer처럼 ConsumerFactory와 앱 spring.kafka.listener.auto-startup을 넣는다
        doAnswer(invocation -> {
            ConcurrentKafkaListenerContainerFactory<Object, Object> factory = invocation.getArgument(0);
            factory.setConsumerFactory(invocation.getArgument(1));
            factory.setAutoStartup(appAutoStartup);
            return null;
        }).when(configurer).configure(any(), any());

        var factory = OrderKafkaListenerConfig.factory(configurer, consumerFactory, consumerEnabled, errorHandler,
            deadLetterErrorHandler);
        return factory.createContainer(topics);
    }

    private static ListenerExecutionFailedException listenerFailure(Exception cause) {
        return new ListenerExecutionFailedException("리스너 실패", cause);
    }

    private static ConsumerRecord<String, String> record(String topic, int partition) {
        var record = new ConsumerRecord<>(topic, partition, 5L, "7", "{\"address\":\"" + BODY_MARKER + "\"}");
        record.headers().add("eventType", "seller.changed".getBytes(UTF_8));
        record.headers().add("eventId", "evt-1".getBytes(UTF_8));
        return record;
    }
}
