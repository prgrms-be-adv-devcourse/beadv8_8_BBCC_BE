package com.bbcc.kidly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.support.IntegrationTest;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

@IntegrationTest
class KidlyApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void defaultTimeZoneIsKst() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
    }
}
