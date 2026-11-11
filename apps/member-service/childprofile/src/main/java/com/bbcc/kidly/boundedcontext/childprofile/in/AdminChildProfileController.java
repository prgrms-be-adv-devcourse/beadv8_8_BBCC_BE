package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildChangedRepublishService;
import com.bbcc.kidly.boundedcontext.childprofile.app.ChildGrowthRecordedRepublishService;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.ChildChangedRepublishResponse;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.ChildGrowthRecordedRepublishResponse;
import com.bbcc.kidly.member.support.security.AuthAdmin;
import com.bbcc.kidly.member.support.security.LoginAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 자녀 API. /api/v1/admin/** 라 관리자 주체만 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/admin/children")
@RequiredArgsConstructor
public class AdminChildProfileController {

    private final ChildChangedRepublishService childChangedRepublishService;
    private final ChildGrowthRecordedRepublishService childGrowthRecordedRepublishService;

    @PostMapping("/growth-recorded-events")
    public ChildGrowthRecordedRepublishResponse republishGrowthRecordedEvents(@LoginAdmin AuthAdmin admin) {
        return new ChildGrowthRecordedRepublishResponse(childGrowthRecordedRepublishService.republishAll());
    }

    // 삭제되지 않은 자녀마다 child.changed를 지금 값으로 다시 보내고, 보낸 자녀 수를 돌려준다
    @PostMapping("/changed-events")
    public ChildChangedRepublishResponse republishChangedEvents(@LoginAdmin AuthAdmin admin) {
        return new ChildChangedRepublishResponse(childChangedRepublishService.republishAll());
    }
}
