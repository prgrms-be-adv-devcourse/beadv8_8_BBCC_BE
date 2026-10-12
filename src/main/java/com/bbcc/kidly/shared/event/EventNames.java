package com.bbcc.kidly.shared.event;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@link DomainEvent}의 eventType과 producer 기본값을 만든다.
 */
final class EventNames {

    private static final String EVENT_SUFFIX = "Event";
    private static final Pattern PRODUCER_PACKAGE = Pattern.compile(".*\\.shared\\.([a-z]+)\\.event");

    private EventNames() {
    }

    static String typeOf(Class<?> eventClass) {
        String name = eventClass.getSimpleName();
        if (name.endsWith(EVENT_SUFFIX)) {
            name = name.substring(0, name.length() - EVENT_SUFFIX.length());
        }
        String[] words = name.split("(?=[A-Z])");
        String target = words[0].toLowerCase(Locale.ROOT);
        String pastTense = Arrays.stream(words, 1, words.length)
            .map(word -> word.toLowerCase(Locale.ROOT))
            .collect(Collectors.joining("-"));
        return pastTense.isEmpty() ? target : target + "." + pastTense;
    }

    static String producerOf(String packageName) {
        Matcher matcher = PRODUCER_PACKAGE.matcher(packageName);
        if (!matcher.matches()) {
            throw new IllegalStateException("도메인 이벤트는 shared.{도메인}.event 패키지에 둔다: " + packageName);
        }
        return matcher.group(1);
    }
}
