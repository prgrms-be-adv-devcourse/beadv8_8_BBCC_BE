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

    @PostMapping("/changed-events")
    public ChildChangedRepublishResponse republishChangedEvents(@LoginAdmin AuthAdmin admin) {
        return new ChildChangedRepublishResponse(childChangedRepublishService.republishAll());
    }
}
