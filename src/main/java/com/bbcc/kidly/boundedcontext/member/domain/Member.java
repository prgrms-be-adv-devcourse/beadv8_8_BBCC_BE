package com.bbcc.kidly.boundedcontext.member.domain;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 회원. 소셜 계정으로 가입 · 로그인한다. 판매자 여부는 저장하지 않는다.
 */
@Entity
@Table(name = "member", schema = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    // 나중에 같은 사람의 여러 소셜 계정을 한 회원으로 통합할 수 있게 1:N으로 둔다
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SocialAccount> socialAccounts = new ArrayList<>();

    private Member(String nickname) {
        this.nickname = nickname;
        this.role = MemberRole.USER;
        this.status = MemberStatus.ACTIVE;
    }

    public static Member register(OAuthProvider provider, String providerUserId, String nickname, LocalDateTime now) {
        Member member = new Member(requireNonNull(nickname, "nickname은 필수입니다"));
        member.socialAccounts.add(new SocialAccount(
            member,
            requireNonNull(provider, "provider는 필수입니다"),
            requireNonNull(providerUserId, "providerUserId는 필수입니다"),
            requireNonNull(now, "now는 필수입니다")));
        Assert.hasText(nickname, "nickname은 비어 있을 수 없습니다");
        Assert.hasText(providerUserId, "providerUserId는 비어 있을 수 없습니다");
        return member;
    }

    public boolean canLogin() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }

    public List<SocialAccount> getSocialAccounts() {
        return Collections.unmodifiableList(socialAccounts);
    }
}
