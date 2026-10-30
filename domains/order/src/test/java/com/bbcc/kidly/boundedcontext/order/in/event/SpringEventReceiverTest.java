package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentCompletedEvent;
import com.bbcc.kidly.shared.payment.event.PaymentFailedEvent;
import com.bbcc.kidly.shared.product.event.ProductChangedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductedEvent;
import com.bbcc.kidly.shared.product.event.StockDeductionFailedEvent;
import com.bbcc.kidly.shared.seller.event.SellerChangedEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

/**
 * 주문이 받는 이벤트 7종(상품 · 재고 2 · 판매자 · 자녀 #541, 결제 2 #594)의 Spring 수신:
 * 스위치(kidly.order.kafka.consumer.enabled)가 꺼졌을 때만 받는다(장애 때 되돌리기).
 * 켜져 있으면(기본) Kafka 리스너만 받고 Spring 이벤트는 받지 않는다(이중 처리 없음).
 * 처리 코드는 하나: Spring 수신과 Kafka 리스너 모두 같은 처리 메서드를 부른다.
 */
class SpringEventReceiverTest {

    private static final String ORDER_CLASSES = "classpath*:com/bbcc/kidly/boundedcontext/order/**/*.class";
    private static final Set<Class<?>> CONVERTED_EVENTS = Set.of(ProductChangedEvent.class, StockDeductedEvent.class,
        StockDeductionFailedEvent.class, SellerChangedEvent.class, ChildChangedEvent.class,
        PaymentCompletedEvent.class, PaymentFailedEvent.class);

    private final ProductEventListener productEventListener = mock();
    private final StockEventListener stockEventListener = mock();
    private final SellerReplicaEventListener sellerReplicaEventListener = mock();
    private final ChildProfileEventListener childProfileEventListener = mock();
    private final PaymentEventListener paymentEventListener = mock();

    @ParameterizedTest(name = "enabled={0} → Spring 수신 빈 {1}")
    @CsvSource({"true, false", "false, true"})
    @DisplayName("스위치가 꺼졌을 때만 Spring 수신 빈을 만든다")
    void createdOnlyWhenSwitchIsOff(String enabled, boolean created) {
        app().withPropertyValues("kidly.order.kafka.consumer.enabled=" + enabled)
            .run(context -> assertThat(context.containsBean("springEventReceiver")).isEqualTo(created));
    }

    @Test
    @DisplayName("스위치 값이 없으면 Spring 수신 빈을 만들지 않는다(기본은 Kafka로 받음)")
    void notCreatedWhenSwitchIsMissing() {
        app().run(context -> assertThat(context).doesNotHaveBean(SpringEventReceiver.class));
    }

    @Test
    @DisplayName("Spring 수신은 받은 이벤트를 Kafka 리스너와 같은 처리 메서드에 그대로 넘긴다")
    void delegatesToSameProcessing() {
        var receiver = new SpringEventReceiver(productEventListener, stockEventListener, sellerReplicaEventListener,
            childProfileEventListener, paymentEventListener);
        var productChanged = new ProductChangedEvent(null, null, null);
        var stockDeducted = new StockDeductedEvent(null, null, null);
        var stockDeductionFailed = new StockDeductionFailedEvent(null, null, null);
        var sellerChanged = new SellerChangedEvent(null, null, null);
        var childChanged = new ChildChangedEvent(null, null, null);
        var paymentCompleted = new PaymentCompletedEvent(null, null, null);
        var paymentFailed = new PaymentFailedEvent(null, null, null);

        receiver.onProductChanged(productChanged);
        receiver.onStockDeducted(stockDeducted);
        receiver.onStockDeductionFailed(stockDeductionFailed);
        receiver.onSellerChanged(sellerChanged);
        receiver.onChildChanged(childChanged);
        receiver.onPaymentCompleted(paymentCompleted);
        receiver.onPaymentFailed(paymentFailed);

        verify(productEventListener).onProductChanged(productChanged);
        verify(stockEventListener).onStockDeducted(stockDeducted);
        verify(stockEventListener).onStockDeductionFailed(stockDeductionFailed);
        verify(sellerReplicaEventListener).onSellerChanged(sellerChanged);
        verify(childProfileEventListener).onChildChanged(childChanged);
        verify(paymentEventListener).onPaymentCompleted(paymentCompleted);
        verify(paymentEventListener).onPaymentFailed(paymentFailed);
    }

    @Test
    @DisplayName("주문 안에서 7종을 Spring 이벤트로 받는 곳은 Spring 수신 빈 하나뿐이다(켜져 있으면 받는 곳이 없다)")
    void onlyReceiverListensToConvertedEvents() {
        List<String> listeners = domainEventListenerMethods().stream()
            .filter(method -> CONVERTED_EVENTS.contains(method.getParameterTypes()[0]))
            .map(method -> method.getDeclaringClass().getSimpleName() + "." + method.getName())
            .toList();

        assertThat(listeners).containsExactlyInAnyOrder("SpringEventReceiver.onProductChanged",
            "SpringEventReceiver.onStockDeducted", "SpringEventReceiver.onStockDeductionFailed",
            "SpringEventReceiver.onSellerChanged", "SpringEventReceiver.onChildChanged",
            "SpringEventReceiver.onPaymentCompleted", "SpringEventReceiver.onPaymentFailed");
    }

    @Test
    @DisplayName("처리 메서드는 부르는 쪽 트랜잭션에 합류하고 없으면 새로 연다(Spring 수신은 한 트랜잭션, Kafka는 자기 트랜잭션)")
    void processingMethodsAreTransactional() throws NoSuchMethodException {
        List<Method> processing = List.of(
            ProductEventListener.class.getMethod("onProductChanged", ProductChangedEvent.class),
            StockEventListener.class.getMethod("onStockDeducted", StockDeductedEvent.class),
            StockEventListener.class.getMethod("onStockDeductionFailed", StockDeductionFailedEvent.class),
            SellerReplicaEventListener.class.getMethod("onSellerChanged", SellerChangedEvent.class),
            ChildProfileEventListener.class.getMethod("onChildChanged", ChildChangedEvent.class));

        assertThat(processing).allSatisfy(method -> {
            Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
            assertThat(transactional).isNotNull();
            assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
        });
    }

    @Test
    @DisplayName("결제 처리 메서드는 트랜잭션을 열지 않는다(#532: 반영 · 보관 · 취소가 각자 트랜잭션에서 커밋, Spring 수신은 그 바깥)")
    void paymentProcessingMethodsAreNotTransactional() throws NoSuchMethodException {
        List<Method> processing = List.of(
            PaymentEventListener.class.getMethod("onPaymentCompleted", PaymentCompletedEvent.class),
            PaymentEventListener.class.getMethod("onPaymentFailed", PaymentFailedEvent.class));

        assertThat(processing).allSatisfy(method -> {
            assertThat(AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class)).isNull();
            assertThat(method.isAnnotationPresent(DomainEventListener.class)).isFalse();
        });
    }

    private ApplicationContextRunner app() {
        return new ApplicationContextRunner()
            .withUserConfiguration(SwitchProperties.class, SpringEventReceiver.class)
            .withBean(ProductEventListener.class, () -> productEventListener)
            .withBean(StockEventListener.class, () -> stockEventListener)
            .withBean(SellerReplicaEventListener.class, () -> sellerReplicaEventListener)
            .withBean(ChildProfileEventListener.class, () -> childProfileEventListener)
            .withBean(PaymentEventListener.class, () -> paymentEventListener);
    }

    @EnableConfigurationProperties(OrderKafkaConsumerProperties.class)
    static class SwitchProperties {
    }

    // 주문 패키지의 모든 클래스(조건부 빈 포함 — 컴포넌트 스캐너는 @Conditional을 평가해 빼므로 쓰지 않는다)에서
    // @DomainEventListener 메서드를 모은다. 테스트 클래스는 뺀다
    private static List<Method> domainEventListenerMethods() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            var readers = new CachingMetadataReaderFactory(resolver);
            List<Method> methods = new ArrayList<>();
            for (Resource resource : resolver.getResources(ORDER_CLASSES)) {
                String className = readers.getMetadataReader(resource).getClassMetadata().getClassName();
                if (className.endsWith("Test") || className.contains("Test$")) {
                    continue;
                }
                Stream.of(ClassUtils.resolveClassName(className, null).getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(DomainEventListener.class))
                    .forEach(methods::add);
            }
            return methods;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
