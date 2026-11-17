package com.bbcc.kidly.boundedcontext.recommend.in.activity;

import com.bbcc.kidly.boundedcontext.recommend.app.activity.ActivityRecordService;
import com.bbcc.kidly.boundedcontext.recommend.app.activity.dto.ProductViewRequest;
import com.bbcc.kidly.boundedcontext.recommend.app.activity.dto.ProductViewType;
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
 * 상세 진입은 다른 도메인이 보내는 이벤트가 없어 화면이 직접 부른다.
 * 상품 칸에서 담기 팝업을 연 것도 같은 이유로 여기서 받는다.
 */
@RestController
@RequiredArgsConstructor
public class ProductViewController {

    private final ActivityRecordService activityRecordService;

    @PostMapping("/api/v1/recommendations/product-views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@LoginMember AuthMember member, @Valid @RequestBody ProductViewRequest request) {
        if (request.viewType() == ProductViewType.QUICK) {
            activityRecordService.recordProductQuickViewed(member.id(), request.productId(), request.childId());
            return;
        }
        activityRecordService.recordProductViewed(member.id(), request.productId(), request.childId());
    }
}
