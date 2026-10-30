package com.bbcc.kidly.boundedcontext.order;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.LoggerFactory;

/**
 * 테스트 동안 모든 스레드의 로그 이벤트를 MDC와 함께 모은다(#603). 로그 설정은 바꾸지 않고 루트 로거에 목록 appender를 잠시 붙인다.
 * 텍스트 패턴에는 traceId만 나오므로 MDC 칸 전체는 이벤트로 본다. 테스트가 끝나면 {@link #close()}로 뗀다.
 */
public final class CapturedLogs implements AutoCloseable {

    private final Logger root;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private CapturedLogs() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        this.root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender.setContext(context);
        appender.start();
        root.addAppender(appender);
    }

    public static CapturedLogs start() {
        return new CapturedLogs();
    }

    /**
     * 메시지(인자를 채운 문장)에 texts가 모두 들어간 첫 이벤트의 MDC.
     */
    public Optional<Map<String, String>> mdcOf(String... texts) {
        return events().stream()
            .filter(event -> Arrays.stream(texts).allMatch(event.getFormattedMessage()::contains))
            .findFirst()
            .map(ILoggingEvent::getMDCPropertyMap);
    }

    public List<ILoggingEvent> events() {
        synchronized (appender.list) {
            return List.copyOf(appender.list);
        }
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
