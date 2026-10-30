package com.bbcc.kidly.boundedcontext.order.in.event;

import com.bbcc.kidly.boundedcontext.order.app.deadletter.dto.ReceiveDeadLetterCommand;
import java.nio.ByteBuffer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;

/**
 * 주문 DLT 메시지를 저장할 값으로 읽는다(#601). 원래 위치 · 예외는 DeadLetterPublishingRecoverer가 붙인 DLT 헤더에서,
 * eventType · eventId는 DLT로 함께 복사된 원래 헤더에서 읽는다. 읽지 못하는 값이 있어도 실패하지 않는다(저장은 계속):
 * 원래 위치 헤더가 없으면 DLT 자신의 위치, 예외 헤더가 없으면 null.
 */
final class OrderDeadLetterRecords {

    private OrderDeadLetterRecords() {
    }

    static ReceiveDeadLetterCommand command(ConsumerRecord<String, String> record) {
        String topic = OrderKafkaMessages.header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        Header partition = record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION);
        Header offset = record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET);
        // 바깥 예외는 리스너 호출을 감싼 예외일 수 있어 원인 예외를 먼저 쓴다
        String cause = OrderKafkaMessages.header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN);
        return new ReceiveDeadLetterCommand(
            topic == null ? record.topic() : topic,
            partition == null || partition.value().length != Integer.BYTES
                ? record.partition() : ByteBuffer.wrap(partition.value()).getInt(),
            offset == null || offset.value().length != Long.BYTES
                ? record.offset() : ByteBuffer.wrap(offset.value()).getLong(),
            record.key(),
            OrderKafkaMessages.header(record, OrderKafkaMessages.EVENT_TYPE),
            OrderKafkaMessages.header(record, OrderKafkaMessages.EVENT_ID),
            record.value(),
            cause == null ? OrderKafkaMessages.header(record, KafkaHeaders.DLT_EXCEPTION_FQCN) : cause,
            OrderKafkaMessages.header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
            OrderKafkaMessages.header(record, KafkaHeaders.DLT_EXCEPTION_STACKTRACE));
    }
}
