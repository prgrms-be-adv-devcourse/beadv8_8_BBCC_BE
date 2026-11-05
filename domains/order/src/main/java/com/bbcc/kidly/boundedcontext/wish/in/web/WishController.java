package com.bbcc.kidly.boundedcontext.wish.in.web;

import com.bbcc.kidly.boundedcontext.wish.app.WishService;
import com.bbcc.kidly.boundedcontext.wish.app.dto.AddWishItemResult;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishActor;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.AddWishItemRequest;
import com.bbcc.kidly.boundedcontext.wish.in.web.dto.WishResponse;
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
 * 조르기(부모 · 자녀 공용 장바구니) API(#640). 경로는 장바구니(/cart · /cart/items/{id})와 같은 규칙이다(#718):
 * 자녀 한 명의 공용 장바구니는 /wish?childId=, 줄은 /wish/items/{wishItemId}.
 * 부모(회원 로그인)는 자기 자녀의 목록을, 자녀(자녀 로그인, #906)는 자기 프로필의 목록만 다룬다({@link WishCaller}).
 * 남의 자녀 목록은 없는 것으로 본다(404), 자녀 계정 연결을 확인할 수 없으면 403, 로그인이 없으면 401.
 * 스위치 kidly.order.wish.enabled가 false면 이 API가 없다.
 */
@RestController
@RequestMapping(WishController.WISH_PATH)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kidly.order.wish.enabled", havingValue = "true", matchIfMissing = true)
public class WishController {

    public static final String WISH_PATH = "/api/v1/wish";
    public static final String WISH_ITEMS_PATH = WISH_PATH + "/items";

    private final WishService wishService;

    /**
     * 새 줄이면 201 + Location, 같은 옵션 줄에 수량을 더했으면 200(장바구니와 같음).
     */
    @PostMapping("/items")
    public ResponseEntity<WishResponse.Line> add(
        @WishCaller WishActor actor,
        @Valid @RequestBody AddWishItemRequest request) {
        AddWishItemResult result = wishService.add(
            actor, request.childId(), request.optionId(), request.quantity());
        WishResponse.Line body = WishResponse.Line.from(result.line());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create(WISH_ITEMS_PATH + "/" + body.wishItemId())).body(body);
    }

    @GetMapping
    public WishResponse getWishes(@WishCaller WishActor actor, @RequestParam Long childId) {
        return WishResponse.from(wishService.getWishes(actor, childId));
    }

    @DeleteMapping("/items/{wishItemId}")
    public ResponseEntity<Void> remove(@WishCaller WishActor actor, @PathVariable Long wishItemId) {
        wishService.remove(actor, wishItemId);
        return ResponseEntity.noContent().build();
    }
}
