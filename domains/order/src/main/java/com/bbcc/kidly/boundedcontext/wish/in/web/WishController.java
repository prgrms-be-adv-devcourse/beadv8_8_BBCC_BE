package com.bbcc.kidly.boundedcontext.wish.in.web;

import com.bbcc.kidly.boundedcontext.wish.app.WishService;
import com.bbcc.kidly.boundedcontext.wish.app.dto.AddWishItemResult;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActors;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.AddWishItemRequest;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.WishResponse;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조르기(부모 · 자녀 공용 장바구니) API(#640). 자원은 조르기 줄(wish)이고, 자녀 한 명의 목록은 childId로 거른다.
 * 지금은 로그인한 부모가 자기 자녀의 목록만 다룬다(자녀 계정은 회원 쪽 인증이 준비되면 연결).
 * 스위치 kidly.order.wish.enabled가 false면 이 API가 없다.
 */
@RestController
@RequestMapping(WishController.WISHES_PATH)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishController {

    public static final String WISHES_PATH = "/api/v1/wishes";

    private final WishService wishService;
    private final WishActors wishActors;

    /**
     * 새 줄이면 201 + Location, 같은 옵션 줄에 수량을 더했으면 200(장바구니와 같음).
     */
    @PostMapping
    public ResponseEntity<WishResponse.Line> add(
        @LoginMember AuthMember member,
        @Valid @RequestBody AddWishItemRequest request) {
        AddWishItemResult result = wishService.add(
            wishActors.parent(member.id()), request.childId(), request.optionId(), request.quantity());
        WishResponse.Line body = WishResponse.Line.from(result.line());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create(WISHES_PATH + "/" + body.wishItemId())).body(body);
    }

    @GetMapping
    public WishResponse getWishes(@LoginMember AuthMember member, @RequestParam Long childId) {
        return WishResponse.from(wishService.getWishes(wishActors.parent(member.id()), childId));
    }

    @DeleteMapping("/{wishItemId}")
    public ResponseEntity<Void> remove(@LoginMember AuthMember member, @PathVariable Long wishItemId) {
        wishService.remove(wishActors.parent(member.id()), wishItemId);
        return ResponseEntity.noContent().build();
    }
}
