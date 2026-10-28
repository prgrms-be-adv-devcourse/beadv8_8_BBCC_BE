package com.bbcc.kidly.boundedcontext.notification.app;

/**
 * 회원가입 환영 메일. 가입 이벤트에 닉네임이 없어 넣지 않는다. 문구는 기획과 맞춘다.
 */
public record WelcomeEmail(String title, String body) {

    private static final String TITLE = "[Kidly] 가입을 환영합니다";
    private static final String BODY = """
        Kidly에 오신 것을 환영합니다.

        자녀의 키와 체형을 등록하면 지금 잘 맞는 사이즈의 옷을 골라 드려요.
        아이가 자라면 정보만 바꿔 주세요. 추천도 함께 바뀝니다.

        지금 둘러보기: %s
        """;

    public static WelcomeEmail compose(String siteUrl) {
        return new WelcomeEmail(TITLE, BODY.formatted(siteUrl));
    }
}
