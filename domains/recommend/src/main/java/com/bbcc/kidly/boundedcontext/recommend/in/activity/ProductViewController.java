package com.bbcc.kidly.boundedcontext.recommend.in.activity;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
import com.bbcc.kidly.boundedcontext.recommend.app.activity.dto.ProductViewRequest;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원이 상품 상세에 들어간 것을 활동 기록으로 남기는 API다.
 * 상세 진입은 다른 도메인이 보내는 이벤트가 없어 화면이 직접 부른다.
 */
@RestController
@RequiredArgsConstructor
public class ProductViewController {

    private final ActivityRecordService activityRecordService;

    @PostMapping("/api/v1/recommendations/product-views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@LoginMember AuthMember member, @Valid @RequestBody ProductViewRequest request) {
        activityRecordService.recordProductViewed(member.id(), request.productId());
    }
}
