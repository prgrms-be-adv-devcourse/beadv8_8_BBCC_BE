package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 기획서 7.1 공통 초기 데이터 중 자녀를 회원 초기 데이터의 보호자(회원 1)에게 넣는다. 자녀가 한 명도 없을 때만 넣는다.
 * 키 115cm는 상품 A의 110 · 120 사이즈에 모두 걸쳐서 선호 핏으로 고르는 경우까지 시연할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildProfileInitialDataService {

    private static final Long PARENT_ID = 1L;

    private final ChildProfileService childProfileService;
    private final ChildProfileRepository childProfileRepository;

    public void load() {
        if (childProfileRepository.count() > 0) {
            log.info("자녀가 이미 있어 자녀 초기 데이터를 넣지 않는다");
            return;
        }
        childProfileService.register(PARENT_ID, new ChildProfileRequest("테스트", LocalDate.of(2019, 1, 1),
            Gender.MALE, new BigDecimal("115.0"), new BigDecimal("20.0"), BodyType.NORMAL, PreferredFit.LOOSE));
        log.info("자녀 초기 데이터를 넣었다");
    }
}
