package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.notification.app.MemberContactService;
import com.bbcc.kidly.boundedcontext.notification.out.JpaMemberContactRepository;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class MemberContactServiceIntegrationTest {

    private static final Long MEMBER_ID = 1L;
    private static final String EMAIL = "parent@kidly.co.kr";

    @Autowired
    private MemberContactService memberContactService;

    @Autowired
    private JpaMemberContactRepository jpaMemberContactRepository;

    @Test
    @DisplayName("저장하면 회원 ID로 이메일을 찾는다")
    void saveAndFind() {
        memberContactService.save(MEMBER_ID, EMAIL);

        assertThat(memberContactService.findEmail(MEMBER_ID)).contains(EMAIL);
    }

    @Test
    @DisplayName("같은 회원을 다시 저장해도 한 건이고 처음 이메일을 그대로 둔다")
    void saveTwice() {
        memberContactService.save(MEMBER_ID, EMAIL);
        memberContactService.save(MEMBER_ID, "other@kidly.co.kr");

        assertThat(jpaMemberContactRepository.findAll()).singleElement()
            .satisfies(contact -> assertThat(contact.getEmail()).isEqualTo(EMAIL));
    }

    @Test
    @DisplayName("이메일 없이 가입한 회원은 저장하지 않는다")
    void skipWithoutEmail() {
        memberContactService.save(MEMBER_ID, null);

        assertThat(jpaMemberContactRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("지우면 찾지 못하고, 없는 회원을 지워도 실패하지 않는다")
    void delete() {
        memberContactService.save(MEMBER_ID, EMAIL);

        memberContactService.delete(MEMBER_ID);
        memberContactService.delete(MEMBER_ID);

        assertThat(memberContactService.findEmail(MEMBER_ID)).isEmpty();
    }
}
