package com.bbcc.kidly.boundedcontext.member.domain;

import java.util.Optional;

/**
 * 회원 닉네임(값 객체). 2~20자이고 앞뒤에 공백이 없다. 회원이 바꾸는 값은 요청 DTO가, 제공자 닉네임은 {@link #fromProvider}가 맞춘다.
 * 회원끼리 겹치지 않는지는 {@link NicknamePolicy}가 확인하고, DB 유니크 제약이 마지막으로 막는다.
 * {@link Member}는 이것을 받아 문자열로 저장한다.
 */
public record Nickname(String value) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 20;

    // 제공자가 쓸 만한 닉네임을 주지 않으면 이 값에 숫자 4자리를 붙여 가입한다
    public static final Nickname DEFAULT_BASE = new Nickname("회원");

    private static final int NUMBER_LENGTH = 4;
    public static final int MIN_NUMBER = 1000;
    public static final int MAX_NUMBER = 9999;

    // 제공자 닉네임을 규칙에 맞춘다. 앞뒤 공백을 빼고 20자를 넘으면 자른다. 없거나 2자 미만이면 비어 있다
    // 닉네임은 사용자가 제공에 동의해야 오므로 null일 수 있다
    public static Optional<Nickname> fromProvider(String providerNickname) {
        return Optional.ofNullable(providerNickname)
            .map(nickname -> cut(nickname.strip(), MAX_LENGTH))
            .filter(nickname -> nickname.length() >= MIN_LENGTH)
            .map(Nickname::new);
    }

    // 숫자 4자리를 붙인다. 붙여도 20자를 넘지 않게 앞부분만 쓴다
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
