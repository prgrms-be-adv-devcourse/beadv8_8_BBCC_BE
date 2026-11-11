package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ChildProfileGrowthRepositoryIntegrationTest {

    @Autowired
    private ChildProfileRepository repository;

    @Test
    @DisplayName("삭제되지 않은 자녀만 성장 이력과 함께 한 번에 읽는다")
    void loadsLiveChildrenWithRecords() {
        var live = ChildProfileFixture.child(1L);
        live.update(ChildProfileFixture.updateInfo("첫째", new BigDecimal("118.0"), ChildProfileFixture.WEIGHT,
            BodyType.NORMAL, PreferredFit.LOOSE), ChildProfileFixture.NOW.plusDays(30));
        var liveId = repository.save(live).getId();
        var deleted = ChildProfileFixture.child(2L);
        deleted.delete(ChildProfileFixture.NOW);
        repository.save(deleted);

        var children = repository.findAllWithGrowthRecords();

        assertThat(children).singleElement().satisfies(child -> {
            assertThat(child.getId()).isEqualTo(liveId);
            assertThat(child.getGrowthRecords()).hasSize(2);
        });
    }
}
