package com.bbcc.kidly.boundedcontext.wish.in.web;

import com.bbcc.kidly.boundedcontext.wish.app.WishRequestService;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishRequestView;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.ApproveWishRequest;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.RejectWishRequest;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.SendWishRequest;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.WishRequestResponse;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.WishTimelineResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조르기 API(#701). 자원은 조르기이고 공용 장바구니 아래(/wish/requests, #718)에 둔다. 자녀 한 명의 목록은 childId로 거른다.
 * 승인 · 거절은 조르기의 하위 자원(approval · rejection)을 만드는 것으로 둔다. 보호자(회원 로그인)와 자녀(자녀 로그인, #906)가 부른다
 * ({@link WishCaller}): 자녀는 자기 프로필의 조르기 보내기 · 조회 · 타임라인만, 승인 · 거절은 보호자만이다(자녀면 403).
 * 스위치 kidly.order.wish.enabled가 false면 이 API가 없다.
 */
@RestController
@RequestMapping(WishRequestController.WISH_REQUESTS_PATH)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishRequestController {

    public static final String WISH_REQUESTS_PATH = WishController.WISH_PATH + "/requests";

    private final WishRequestService wishRequestService;

    @PostMapping
    public ResponseEntity<WishRequestResponse> send(
        @WishCaller WishActor actor,
        @Valid @RequestBody SendWishRequest request) {
        WishRequestView view = wishRequestService.send(
            actor, request.childId(), request.wishItemIds(), request.message());
        WishRequestResponse body = WishRequestResponse.from(view);
        return ResponseEntity.created(URI.create(WISH_REQUESTS_PATH + "/" + body.requestId())).body(body);
    }

    @GetMapping
    public List<WishRequestResponse> getRequests(@WishCaller WishActor actor, @RequestParam Long childId) {
        return wishRequestService.getRequests(actor, childId).stream()
            .map(WishRequestResponse::from)
            .toList();
    }

    @GetMapping("/{requestId}")
    public WishRequestResponse getRequest(@WishCaller WishActor actor, @PathVariable Long requestId) {
        return WishRequestResponse.from(wishRequestService.getRequest(actor, requestId));
    }

    /**
     * 조르기 단계(보냄 → 승인 · 거절)에 바로 결제한 주문의 타임라인을 이어 붙인다(#702).
     */
    @GetMapping("/{requestId}/timeline")
    public WishTimelineResponse getTimeline(@WishCaller WishActor actor, @PathVariable Long requestId) {
        return WishTimelineResponse.from(wishRequestService.getTimeline(actor, requestId));
    }

    @PostMapping("/{requestId}/approval")
    public WishRequestResponse approve(
        @WishCaller WishActor actor,
        @PathVariable Long requestId,
        @Valid @RequestBody ApproveWishRequest request) {
        return WishRequestResponse.from(
            wishRequestService.approve(actor, requestId, request.toCommand()));
    }

    @PostMapping("/{requestId}/rejection")
    public WishRequestResponse reject(
        @WishCaller WishActor actor,
        @PathVariable Long requestId,
        @Valid @RequestBody RejectWishRequest request) {
        return WishRequestResponse.from(
            wishRequestService.reject(actor, requestId, request.reason()));
    }
}
