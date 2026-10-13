package com.bbcc.kidly.boundedcontext.childprofile.in;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.in.dto.ChildResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호자의 자녀 프로필 API. 본인 자녀만 다루고, 없는 · 남의 · 삭제된 자녀는 모두 404다.
 */
@RestController
@RequiredArgsConstructor
public class ChildController {

    private static final String CHILDREN_PATH = "/api/v1/children";

    private final ChildProfileService childProfileService;

    @PostMapping(CHILDREN_PATH)
    @ResponseStatus(HttpStatus.CREATED)
    public ChildResponse register(
        @LoginMember Long memberId, @Valid @RequestBody ChildProfileRequest request, HttpServletResponse response) {
        var child = childProfileService.register(memberId, request);
        response.setHeader(HttpHeaders.LOCATION, CHILDREN_PATH + "/" + child.id());
        return ChildResponse.from(child);
    }

    @GetMapping(CHILDREN_PATH)
    public List<ChildResponse> list(@LoginMember Long memberId) {
        return childProfileService.getChildren(memberId).stream().map(ChildResponse::from).toList();
    }

    @GetMapping(CHILDREN_PATH + "/{childId}")
    public ChildResponse get(@LoginMember Long memberId, @PathVariable Long childId) {
        return ChildResponse.from(childProfileService.getChild(memberId, childId));
    }

    // 모든 필드를 담아 보내는 전체 교체다
    @PatchMapping(CHILDREN_PATH + "/{childId}")
    public ChildResponse update(
        @LoginMember Long memberId, @PathVariable Long childId, @Valid @RequestBody ChildProfileRequest request) {
        return ChildResponse.from(childProfileService.update(memberId, childId, request));
    }

    @DeleteMapping(CHILDREN_PATH + "/{childId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginMember Long memberId, @PathVariable Long childId) {
        childProfileService.delete(memberId, childId);
    }

    @GetMapping(CHILDREN_PATH + "/{childId}/growth-records")
    public List<GrowthRecordResponse> growthRecords(@LoginMember Long memberId, @PathVariable Long childId) {
        return childProfileService.getGrowthRecords(memberId, childId).stream()
            .map(GrowthRecordResponse::from)
            .toList();
    }
}
