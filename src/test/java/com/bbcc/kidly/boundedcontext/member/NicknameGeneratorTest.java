package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.member.app.support.NicknameGenerator;
import com.bbcc.kidly.boundedcontext.member.domain.Member;
import com.bbcc.kidly.boundedcontext.member.domain.MemberRepository;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NicknameGeneratorTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private NicknameGenerator nicknameGenerator;

    @Test
    @DisplayName("앞뒤 공백을 뺀다")
    void stripsWhitespace() {
        assertThat(generate("  하은맘  ")).isEqualTo("하은맘");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "가", " 가 "})
    @DisplayName("없거나 공백뿐이거나 2자 미만이면 회원 + 숫자 4자리다")
    void defaultNickname(String providerNickname) {
        assertThat(generate(providerNickname)).matches("회원\\d{4}");
    }

    @Test
    @DisplayName("정확히 20자는 그대로 쓴다")
    void keepsTwentyChars() {
        assertThat(generate("가".repeat(20))).isEqualTo("가".repeat(20));
    }

    @Test
    @DisplayName("21자 이상은 20자로 자른다")
    void cutsLongNickname() {
        assertThat(generate("가".repeat(30))).isEqualTo("가".repeat(20));
    }

    @Test
    @DisplayName("자른 끝이 공백이면 뺀다")
    void stripsTrailingSpaceAfterCut() {
        assertThat(generate("가".repeat(19) + " 나나")).isEqualTo("가".repeat(19));
    }

    @Test
    @DisplayName("자르는 자리가 서로게이트 쌍의 앞쪽이면 그 앞에서 자른다")
    void doesNotSplitSurrogatePair() {
        assertThat(generate("가".repeat(19) + "😀나")).isEqualTo("가".repeat(19));
    }

    @Test
    @DisplayName("다른 회원이 쓰고 있으면 앞 16자에 숫자 4자리를 붙인다")
    void duplicateGetsNumber() {
        given(memberRepository.existsByNickname("가".repeat(20))).willReturn(true);

        var nickname = generate("가".repeat(20));

        assertThat(nickname).matches("가{16}\\d{4}");
        assertThat(nickname).hasSizeLessThanOrEqualTo(Member.NICKNAME_MAX_LENGTH);
    }

    @Test
    @DisplayName("붙인 값도 계속 겹치면 5번 뽑은 뒤 마지막 후보를 돌려준다")
    void givesUpAfterMaxAttempts() {
        given(memberRepository.existsByNickname(anyString())).willReturn(true);

        var nickname = generate("소셜회원");

        assertThat(nickname).matches("소셜회원\\d{4}");
        // 원래 값 1번 + 후보 4번 (다섯 번째 후보는 확인 없이 돌려준다)
        verify(memberRepository, times(5)).existsByNickname(anyString());
    }

    // 결과는 항상 회원 닉네임 규칙을 만족한다 (규칙을 어기면 Member.register가 예외를 던진다)
    private String generate(String providerNickname) {
        var nickname = nicknameGenerator.generate(providerNickname);
        Member.register(OAuthProvider.KAKAO, "kakao-1", nickname, MemberFixture.NOW);
        return nickname;
    }
}
