package com.bbcc.kidly.boundedcontext.notification.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 알림이 받는 사람을 찾을 때 쓰는 회원 이메일 복사본. 회원 서비스를 부르지 않고 이벤트로 받아 둔다.
 * 이메일은 가입 뒤 바꿀 수 없어 만든 뒤 바뀌지 않는다. 탈퇴하면 행을 지운다.
 */
@Entity
@Table(schema = "notification", name = "member_contact")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberContact extends BaseTimeEntity {

    private static final int EMAIL_MAX_LENGTH = 320;

    @Id
    private Long memberId;

    @Column(nullable = false, length = EMAIL_MAX_LENGTH)
    private String email;

    public static MemberContact of(Long memberId, String email) {
        Assert.notNull(memberId, "회원 ID는 비어 있을 수 없습니다");
        Assert.hasText(email, "이메일은 비어 있을 수 없습니다");
        Assert.isTrue(email.length() <= EMAIL_MAX_LENGTH, "이메일은 320자를 넘을 수 없습니다");
        var contact = new MemberContact();
        contact.memberId = memberId;
        contact.email = email;
        return contact;
    }
}
