package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.ChildProfileResponse;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.GrowthRecordResponse;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호자의 자녀 프로필 API. 본인 자녀만 다루고, 없는 · 남의 · 삭제된 자녀는 모두 404다.
 */
@RestController
@RequestMapping("/api/v1/children")
@RequiredArgsConstructor
public class ChildProfileController {

    private final ChildProfileService childProfileService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChildProfileResponse register(
        @LoginMember Long memberId, @Valid @RequestBody ChildProfileRequest request, HttpServletResponse response) {
        var child = childProfileService.register(memberId, request);
        response.setHeader(HttpHeaders.LOCATION, "/api/v1/children/" + child.id());
        return ChildProfileResponse.from(child);
    }

    @GetMapping
    public List<ChildProfileResponse> list(@LoginMember Long memberId) {
        return childProfileService.getChildren(memberId).stream().map(ChildProfileResponse::from).toList();
    }

    @GetMapping("/{childId}")
    public ChildProfileResponse get(@LoginMember Long memberId, @PathVariable Long childId) {
        return ChildProfileResponse.from(childProfileService.getChild(memberId, childId));
    }

    // 모든 필드를 담아 보내는 전체 교체다
    @PatchMapping("/{childId}")
    public ChildProfileResponse update(
        @LoginMember Long memberId, @PathVariable Long childId, @Valid @RequestBody ChildProfileRequest request) {
        return ChildProfileResponse.from(childProfileService.update(memberId, childId, request));
    }

    @DeleteMapping("/{childId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginMember Long memberId, @PathVariable Long childId) {
        childProfileService.delete(memberId, childId);
    }

    @GetMapping("/{childId}/growth-records")
    public List<GrowthRecordResponse> growthRecords(@LoginMember Long memberId, @PathVariable Long childId) {
        return childProfileService.getGrowthRecords(memberId, childId).stream()
            .map(GrowthRecordResponse::from)
            .toList();
    }
}
