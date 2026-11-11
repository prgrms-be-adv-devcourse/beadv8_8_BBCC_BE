package com.bbcc.kidly.boundedcontext.recommend.in.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.ReferenceGarmentService;
import com.bbcc.kidly.boundedcontext.recommend.app.referencegarment.dto.ReferenceGarmentRequest;
import com.bbcc.kidly.boundedcontext.recommend.in.referencegarment.dto.PurchasedGarmentResponse;
import com.bbcc.kidly.boundedcontext.recommend.in.referencegarment.dto.ReferenceGarmentResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReferenceGarmentController {

    private static final String REFERENCE_GARMENTS = "/api/v1/recommendations/children/{childId}/reference-garments";

    private static final String PURCHASES = "/api/v1/recommendations/children/{childId}/purchases";

    private final ReferenceGarmentService referenceGarmentService;

    @GetMapping(REFERENCE_GARMENTS)
    public List<ReferenceGarmentResponse> list(@LoginMember AuthMember member, @PathVariable Long childId) {
        return referenceGarmentService.list(member.id(), childId).stream()
            .map(ReferenceGarmentResponse::from)
            .toList();
    }

    /**
     * size는 1 ~ 100이다.
     */
    @GetMapping(PURCHASES)
    public PageResponse<PurchasedGarmentResponse> listPurchases(@LoginMember AuthMember member,
        @PathVariable Long childId, @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(referenceGarmentService.listPurchasedGarments(member.id(), childId, page, size)
            .map(PurchasedGarmentResponse::from));
    }

    @PutMapping(REFERENCE_GARMENTS + "/{part}")
    public ReferenceGarmentResponse save(@LoginMember AuthMember member, @PathVariable Long childId,
        @PathVariable String part, @Valid @RequestBody ReferenceGarmentRequest request) {
        return ReferenceGarmentResponse.from(referenceGarmentService.save(
            member.id(), childId, part, request.categoryId(), request.measurements()));
    }

    @DeleteMapping(REFERENCE_GARMENTS + "/{part}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginMember AuthMember member, @PathVariable Long childId, @PathVariable String part) {
        referenceGarmentService.delete(member.id(), childId, part);
    }
}
