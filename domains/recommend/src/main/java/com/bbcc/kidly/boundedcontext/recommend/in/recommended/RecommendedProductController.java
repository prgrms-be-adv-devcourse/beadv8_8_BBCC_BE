package com.bbcc.kidly.boundedcontext.recommend.in.recommended;

import com.bbcc.kidly.boundedcontext.recommend.app.recommended.RecommendedProductService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.RecommendReplicaChildAccountService;
import com.bbcc.kidly.boundedcontext.recommend.app.replica.dto.ChildCaller;
import com.bbcc.kidly.boundedcontext.recommend.app.recommended.dto.RecommendedProductPage;
import com.bbcc.kidly.boundedcontext.recommend.in.recommended.dto.RecommendedProductPageResponse;
import com.bbcc.kidly.boundedcontext.recommend.in.recommended.dto.RecommendedProductResponse;
import com.bbcc.kidly.boundedcontext.recommend.in.support.ChildCallerHeaders;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.global.security.AuthChild;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginChild;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원의 자녀에게 맞는 사이즈로 살 수 있는 상품 목록을 선호 점수 순으로 돌려주는 API다.
 * 2쪽부터는 1쪽 응답의 기준 시각 asOf를 그대로 받아 쪽 사이의 순서를 지킨다.
 * 자녀 토큰으로 부르면 childId를 받지 않고 그 계정에 연결된 자녀로 고른다(보내도 무시한다).
 */
@RestController
@RequiredArgsConstructor
public class RecommendedProductController {

    private final RecommendedProductService recommendedProductService;
    private final RecommendReplicaChildAccountService childAccountService;

    @GetMapping("/api/v1/recommendations/products")
    public RecommendedProductPageResponse list(
        @LoginMember AuthMember member,
        @RequestParam Long childId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {
        RecommendedProductPage result =
            recommendedProductService.list(member.id(), childId, categoryId, page, size, asOf);
        return RecommendedProductPageResponse.of(
            PageResponse.from(result.page().map(RecommendedProductResponse::from)), result.asOf());
    }

    @GetMapping(value = "/api/v1/recommendations/products",
        headers = {ChildCallerHeaders.CHILD_ACCOUNT, ChildCallerHeaders.NOT_MEMBER})
    public RecommendedProductPageResponse listForChild(
        @LoginChild AuthChild child,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf) {
        ChildCaller caller = childAccountService.resolve(child.accountId(), child.guardianId());
        RecommendedProductPage result = recommendedProductService.list(
            caller.guardianId(), caller.childProfileId(), categoryId, page, size, asOf);
        return RecommendedProductPageResponse.of(
            PageResponse.from(result.page().map(RecommendedProductResponse::from)), result.asOf());
    }
}
