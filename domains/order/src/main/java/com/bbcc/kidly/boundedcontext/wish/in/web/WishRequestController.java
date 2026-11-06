package com.bbcc.kidly.boundedcontext.wish.in.web;

import com.bbcc.kidly.boundedcontext.wish.app.WishRequestService;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishRequestView;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
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
 * 구매 요청(조르기) API(#999). 자원은 조르기(/wish/requests)이고, 승인 = 결제라 승인 API는 없다(승인자는 예약된 장바구니 칸으로
 * 주문하기를 부른다). 거절 · 철회는 조르기의 하위 자원(rejection · cancellation)을 만드는 것으로 둔다.
 * 회원(보호자)과 자녀(자녀 로그인, #906)가 부른다({@link WishCaller}): 목록 · 상세 · 타임라인은 각자 볼 수 있는 것(보낸 것 + 승인자인 것),
 * 보내기는 자녀만, 거절은 승인자만, 철회는 보낸 사람만이다. 스위치 kidly.order.wish.enabled가 false면 이 API가 없다.
 */
@RestController
@RequestMapping(WishRequestController.WISH_REQUESTS_PATH)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishRequestController {

    public static final String WISH_REQUESTS_PATH = "/api/v1/wish/requests";

    private final WishRequestService wishRequestService;

    @PostMapping
    public ResponseEntity<WishRequestResponse> send(
        @WishCaller WishActor actor,
        @Valid @RequestBody SendWishRequest request) {
        WishRequestView view = wishRequestService.send(actor, request.cartItemIds(), request.message());
        WishRequestResponse body = WishRequestResponse.from(view);
        return ResponseEntity.created(URI.create(WISH_REQUESTS_PATH + "/" + body.requestId())).body(body);
    }

    /**
     * @param childId 받는 자녀로 거른다(선택)
     */
    @GetMapping
    public List<WishRequestResponse> getRequests(@WishCaller WishActor actor,
        @RequestParam(required = false) Long childId) {
        return wishRequestService.getRequests(actor, childId).stream()
            .map(WishRequestResponse::from)
            .toList();
    }

    @GetMapping("/{requestId}")
    public WishRequestResponse getRequest(@WishCaller WishActor actor, @PathVariable Long requestId) {
        return WishRequestResponse.from(wishRequestService.getRequest(actor, requestId));
    }

    /**
     * 조르기 단계(보냄 → 결제 완료 · 거절 · 철회)와 조르기로 산 주문 ID(#702 · #999).
     */
    @GetMapping("/{requestId}/timeline")
    public WishTimelineResponse getTimeline(@WishCaller WishActor actor, @PathVariable Long requestId) {
        return WishTimelineResponse.from(wishRequestService.getTimeline(actor, requestId));
    }

    /**
     * 보낸 사람이 처리 전에 철회한다(#968). 보낸 사람이 아니면 403, 처리된 조르기면 409.
     */
    @PostMapping("/{requestId}/cancellation")
    public WishRequestResponse cancel(@WishCaller WishActor actor, @PathVariable Long requestId) {
        return WishRequestResponse.from(wishRequestService.cancel(actor, requestId));
    }

    /**
     * 승인자가 거절한다. 승인자가 아니면 403, 처리된 조르기면 409.
     */
    @PostMapping("/{requestId}/rejection")
    public WishRequestResponse reject(
        @WishCaller WishActor actor,
        @PathVariable Long requestId,
        @Valid @RequestBody RejectWishRequest request) {
        return WishRequestResponse.from(wishRequestService.reject(actor, requestId, request.reason()));
    }
}
