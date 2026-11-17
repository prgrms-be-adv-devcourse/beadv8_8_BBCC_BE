package com.bbcc.kidly.boundedcontext.member.domain;

import java.util.Optional;

public record Nickname(String value) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 20;

    public static final Nickname DEFAULT_BASE = new Nickname("회원");

    private static final int NUMBER_LENGTH = 4;
    public static final int MIN_NUMBER = 1000;
    public static final int MAX_NUMBER = 9999;

    // 닉네임은 사용자가 제공에 동의해야 오므로 null일 수 있다
    public static Optional<Nickname> fromProvider(String providerNickname) {
        return Optional.ofNullable(providerNickname)
            .map(nickname -> cut(nickname.strip(), MAX_LENGTH))
            .filter(nickname -> nickname.length() >= MIN_LENGTH)
            .map(Nickname::new);
    }

    public Nickname withNumber(int number) {
        return new Nickname(cut(value, MAX_LENGTH - NUMBER_LENGTH) + number);
    }

    // 앞에서 maxLength자까지 자른다. 이모지(서로게이트 쌍)를 반으로 가르지 않고, 잘린 끝의 공백은 뺀다
    private static String cut(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        var end = Character.isHighSurrogate(value.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return value.substring(0, end).strip();
    }
}
