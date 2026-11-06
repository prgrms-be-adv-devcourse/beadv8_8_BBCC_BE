package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.UnknownDeadLetterSourceException;
import com.bbcc.kidly.boundedcontext.order.app.port.DeadLetterReplayer.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;

/**
 * 자기 토픽(kidly.order.events) DLT 다시 처리(#999): 주문은 조르기를 참조하지 않으므로 조르기 리스너가 내놓은
 * {@code Consumer<ConsumerRecord>} 빈으로 넘긴다. 빈이 없으면(조르기 꺼짐) 모르는 원래 토픽, 둘 이상이면 기동하지 않는다.
 */
class KafkaListenerDeadLetterReplayerTest {

    private static final String ORDER_EVENTS = "kidly.order.events";
    private static final Message MESSAGE = new Message(ORDER_EVENTS, 1, 42L, "1", "cart.item-purchased",
        "8b1f9a6e-5c2d-4f1e-9a3b-1c2d3e4f5a6b", "{}");

    @Test
    @DisplayName("원래 토픽이 kidly.order.events면 그 리스너에 원래 위치 · 키 · 본문 · 헤더 그대로 넘긴다")
    void replaysToOrderEventsListener() {
        List<ConsumerRecord<String, String>> received = new ArrayList<>();

        var outcome = replayer(provider(received::add)).replay(MESSAGE);

        assertThat(outcome.handedOff()).isFalse();
        assertThat(received).singleElement().satisfies(record -> {
            assertThat(record.topic()).isEqualTo(ORDER_EVENTS);
            assertThat(record.partition()).isEqualTo(1);
            assertThat(record.offset()).isEqualTo(42L);
            assertThat(record.key()).isEqualTo("1");
            assertThat(record.value()).isEqualTo("{}");
            assertThat(OrderKafkaMessages.header(record, OrderKafkaMessages.EVENT_TYPE))
                .isEqualTo("cart.item-purchased");
        });
    }

    @Test
    @DisplayName("리스너 빈이 없으면(조르기 꺼짐) 모르는 원래 토픽으로 실패한다")
    void unknownWhenNoListener() {
        assertThatThrownBy(() -> replayer(provider()).replay(MESSAGE))
            .isInstanceOf(UnknownDeadLetterSourceException.class);
    }

    @Test
    @DisplayName("리스너 빈이 둘 이상이면 어느 쪽인지 몰라 기동(생성)이 실패한다")
    void failsWhenAmbiguous() {
        Consumer<ConsumerRecord<String, String>> first = record -> { };
        Consumer<ConsumerRecord<String, String>> second = record -> { };

        assertThatThrownBy(() -> replayer(provider(first, second)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(ORDER_EVENTS);
    }

    private static KafkaListenerDeadLetterReplayer replayer(
        ObjectProvider<Consumer<ConsumerRecord<String, String>>> orderEvents) {
        return new KafkaListenerDeadLetterReplayer(mock(ProductEventKafkaListener.class),
            mock(SellerEventKafkaListener.class), mock(ChildProfileEventKafkaListener.class),
            mock(PaymentEventKafkaListener.class), mock(AuthEventKafkaListener.class),
            mock(MemberEventKafkaListener.class), orderEvents);
    }

    // 운영과 같은 방식(빈 팩토리의 제네릭 타입 조회)으로 Consumer<ConsumerRecord<String, String>> 빈을 찾는다
    @SafeVarargs
    private static ObjectProvider<Consumer<ConsumerRecord<String, String>>> provider(
        Consumer<ConsumerRecord<String, String>>... listeners) {
        var beanFactory = new DefaultListableBeanFactory();
        var type = ResolvableType.forClassWithGenerics(Consumer.class,
            ResolvableType.forClassWithGenerics(ConsumerRecord.class, String.class, String.class));
        for (int i = 0; i < listeners.length; i++) {
            Consumer<ConsumerRecord<String, String>> listener = listeners[i];
            var definition = new RootBeanDefinition();
            definition.setTargetType(type);
            definition.setInstanceSupplier(() -> listener);
            beanFactory.registerBeanDefinition("listener" + i, definition);
        }
        return beanFactory.getBeanProvider(type);
    }
}
