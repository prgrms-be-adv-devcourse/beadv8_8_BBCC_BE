package com.bbcc.kidly.boundedcontext.payment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bbcc.kidly.boundedcontext.payment.app.PaymentActionLog;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * 결제 행동 로그(action.payment)를 테스트에서 받아 본다. 다 쓰면 close()로 떼어 낸다.
 */
final class ActionLogCapture implements AutoCloseable {

    private final Logger logger = (Logger) LoggerFactory.getLogger(PaymentActionLog.LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private ActionLogCapture() {
        appender.start();
        logger.addAppender(appender);
    }

    static ActionLogCapture start() {
        return new ActionLogCapture();
    }

    // 남긴 한 줄(action=... key=value ...)
    List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
