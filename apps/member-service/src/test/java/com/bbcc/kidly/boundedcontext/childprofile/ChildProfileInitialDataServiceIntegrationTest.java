package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileInitialDataService;
import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ChildProfileInitialDataServiceIntegrationTest {

    @Autowired
    private ChildProfileInitialDataService childProfileInitialDataService;

    @Autowired
    private ChildProfileService childProfileService;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Test
    @DisplayName("자녀가 없으면 회원 1에게 성장 이력 1건과 함께 자녀 1을 넣는다")
    void load() {
        childProfileInitialDataService.load();

        assertThat(childProfileService.getChildren(1L)).singleElement().satisfies(child -> {
            assertThat(child.id()).isEqualTo(1L);
            assertThat(child.name()).isEqualTo("테스트");
            assertThat(child.heightCm()).isEqualByComparingTo(new BigDecimal("115.0"));
            assertThat(child.preferredFit()).isEqualTo(PreferredFit.LOOSE);
            assertThat(child.growthRecordCount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("자녀가 한 명이라도 있으면 넣지 않는다")
    void skipWhenChildExists() {
        childProfileRepository.save(ChildProfileFixture.child(99L));

        childProfileInitialDataService.load();

        assertThat(childProfileService.getChildren(1L)).isEmpty();
    }
}
