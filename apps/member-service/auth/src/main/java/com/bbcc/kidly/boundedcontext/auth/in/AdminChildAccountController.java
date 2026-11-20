package com.bbcc.kidly.boundedcontext.auth.in;

import com.bbcc.kidly.boundedcontext.auth.app.ChildAccountService;
import com.bbcc.kidly.boundedcontext.auth.in.dto.ChildAccountChangedRepublishResponse;
import com.bbcc.kidly.member.support.security.AuthAdmin;
import com.bbcc.kidly.member.support.security.LoginAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/children/accounts")
@RequiredArgsConstructor
public class AdminChildAccountController {

    private final ChildAccountService childAccountService;

    @PostMapping("/changed-events")
    public ChildAccountChangedRepublishResponse republishChangedEvents(@LoginAdmin AuthAdmin admin) {
        return new ChildAccountChangedRepublishResponse(childAccountService.republishAllChanged());
    }
}
