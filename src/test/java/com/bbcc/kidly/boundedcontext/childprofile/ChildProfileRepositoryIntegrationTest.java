package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfile;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ChildProfileRepositoryIntegrationTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Test
    @DisplayName("본인 자녀를 ID로 찾고, 성장 이력도 함께 읽는다")
    void findByIdAndMemberId() {
        var saved = childProfileRepository.save(ChildProfileFixture.child(MEMBER_ID));

        var found = childProfileRepository.findByIdAndMemberId(saved.getId(), MEMBER_ID);

        // 트랜잭션 밖에서 이력을 읽어도 LazyInitializationException이 나지 않아야 한다
        assertThat(found).hasValueSatisfying(child -> assertThat(child.growthRecordCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("남의 자녀는 찾지 못한다")
    void otherMembersChild() {
        var saved = childProfileRepository.save(ChildProfileFixture.child(MEMBER_ID));

        assertThat(childProfileRepository.findByIdAndMemberId(saved.getId(), OTHER_MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("삭제된 자녀는 찾지 못한다")
    void deletedChild() {
        var child = ChildProfileFixture.child(MEMBER_ID);
        child.delete(ChildProfileFixture.NOW);
        var saved = childProfileRepository.save(child);

        assertThat(childProfileRepository.findByIdAndMemberId(saved.getId(), MEMBER_ID)).isEmpty();
        assertThat(childProfileRepository.findAllByMemberId(MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("getByIdAndMemberId는 없으면 NOT_FOUND를 던진다")
    void getByIdAndMemberIdNotFound() {
        assertThatThrownBy(() -> childProfileRepository.getByIdAndMemberId(999L, MEMBER_ID))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    // 이력이 여러 건이어도(fetch join) 같은 자녀가 두 번 나오지 않아야 한다
    @Test
    @DisplayName("회원의 자녀 목록을 등록 순으로 주고, 자녀마다 성장 이력을 함께 읽는다")
    void findAllByMemberId() {
        var grown = ChildProfileFixture.child(MEMBER_ID);
        grown.update("첫째", ChildProfileFixture.BIRTH_DATE, Gender.MALE, new BigDecimal("118.0"),
            ChildProfileFixture.WEIGHT, BodyType.NORMAL, PreferredFit.LOOSE, ChildProfileFixture.NOW.plusDays(30));
        var first = childProfileRepository.save(grown);
        var second = childProfileRepository.save(ChildProfileFixture.child(MEMBER_ID));
        childProfileRepository.save(ChildProfileFixture.child(OTHER_MEMBER_ID));

        var children = childProfileRepository.findAllByMemberId(MEMBER_ID);

        assertThat(children).extracting(ChildProfile::getId).containsExactly(first.getId(), second.getId());
        assertThat(children).extracting(ChildProfile::growthRecordCount).containsExactly(2, 1);
        assertThat(children.getFirst().lastRecordedAt()).isEqualTo(ChildProfileFixture.NOW.plusDays(30));
    }
}
