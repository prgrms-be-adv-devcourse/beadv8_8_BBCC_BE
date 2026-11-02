package com.bbcc.kidly.boundedcontext.wish.in.web;

import com.bbcc.kidly.boundedcontext.wish.app.WishRequestService;
import com.bbcc.kidly.boundedcontext.wish.app.dto.WishRequestView;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActors;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.ApproveWishRequest;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.RejectWishRequest;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.SendWishRequest;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.WishRequestResponse;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.WishTimelineResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
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
 * 조르기 API(#701). 자원은 조르기(wish-request)이고, 자녀 한 명의 목록은 childId로 거른다. 승인 · 거절은 조르기의
 * 하위 자원(approval · rejection)을 만드는 것으로 둔다. 지금은 로그인한 보호자가 부른다(자녀 계정은 회원 쪽 인증이 준비되면 연결).
 * 스위치 kidly.order.wish.enabled가 false면 이 API가 없다.
 */
@RestController
@RequestMapping(WishRequestController.WISH_REQUESTS_PATH)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishRequestController {

    public static final String WISH_REQUESTS_PATH = "/api/v1/wish-requests";

    private final WishRequestService wishRequestService;
    private final WishActors wishActors;

    @PostMapping
    public ResponseEntity<WishRequestResponse> send(
        @LoginMember AuthMember member,
        @Valid @RequestBody SendWishRequest request) {
        WishRequestView view = wishRequestService.send(
            wishActors.parent(member.id()), request.childId(), request.wishItemIds(), request.message());
        WishRequestResponse body = WishRequestResponse.from(view);
        return ResponseEntity.created(URI.create(WISH_REQUESTS_PATH + "/" + body.requestId())).body(body);
    }

    @GetMapping
    public List<WishRequestResponse> getRequests(@LoginMember AuthMember member, @RequestParam Long childId) {
        return wishRequestService.getRequests(wishActors.parent(member.id()), childId).stream()
            .map(WishRequestResponse::from)
            .toList();
    }

    @GetMapping("/{requestId}")
    public WishRequestResponse getRequest(@LoginMember AuthMember member, @PathVariable Long requestId) {
        return WishRequestResponse.from(wishRequestService.getRequest(wishActors.parent(member.id()), requestId));
    }

    /**
     * 조르기 단계(보냄 → 승인 · 거절)에 바로 결제한 주문의 타임라인을 이어 붙인다(#702).
     */
    @GetMapping("/{requestId}/timeline")
    public WishTimelineResponse getTimeline(@LoginMember AuthMember member, @PathVariable Long requestId) {
        return WishTimelineResponse.from(wishRequestService.getTimeline(wishActors.parent(member.id()), requestId));
    }

    @PostMapping("/{requestId}/approval")
    public WishRequestResponse approve(
        @LoginMember AuthMember member,
        @PathVariable Long requestId,
        @Valid @RequestBody ApproveWishRequest request) {
        return WishRequestResponse.from(
            wishRequestService.approve(wishActors.parent(member.id()), requestId, request.toCommand()));
    }

    @PostMapping("/{requestId}/rejection")
    public WishRequestResponse reject(
        @LoginMember AuthMember member,
        @PathVariable Long requestId,
        @Valid @RequestBody RejectWishRequest request) {
        return WishRequestResponse.from(
            wishRequestService.reject(wishActors.parent(member.id()), requestId, request.reason()));
    }
}
