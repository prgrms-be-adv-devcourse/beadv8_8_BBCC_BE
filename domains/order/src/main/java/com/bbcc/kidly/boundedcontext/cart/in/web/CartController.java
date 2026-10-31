package com.bbcc.kidly.boundedcontext.cart.in.web;

import com.bbcc.kidly.boundedcontext.cart.app.CartService;
import com.bbcc.kidly.boundedcontext.cart.app.dto.AddCartItemResult;
import com.bbcc.kidly.boundedcontext.cart.in.web.dto.AddCartItemRequest;
import com.bbcc.kidly.boundedcontext.cart.in.web.dto.CartResponse;
import com.bbcc.kidly.boundedcontext.cart.in.web.dto.ChangeCartItemQuantityRequest;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장바구니 API. 로그인한 보호자 본인의 장바구니만 다룬다.
 */
@RestController
@RequestMapping(CartController.CART_PATH)
@RequiredArgsConstructor
public class CartController {

    public static final String CART_PATH = "/api/v1/cart";
    public static final String CART_ITEMS_PATH = CART_PATH + "/items";

    private final CartService cartService;

    /**
     * 새 줄이면 201 + Location, 같은 줄에 수량을 더했으면 200.
     */
    @PostMapping("/items")
    public ResponseEntity<CartResponse.Line> addItem(
        @LoginMember AuthMember member,
        @Valid @RequestBody AddCartItemRequest request) {
        AddCartItemResult result = cartService.addItem(
            member.id(), request.optionId(), request.childId(), request.quantity());
        CartResponse.Line body = CartResponse.Line.from(result.line());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create(CART_ITEMS_PATH + "/" + body.cartItemId())).body(body);
    }

    @GetMapping
    public CartResponse getCart(@LoginMember AuthMember member) {
        return CartResponse.from(cartService.getCart(member.id()));
    }

    @PatchMapping("/items/{cartItemId}")
    public CartResponse.Line changeQuantity(
        @LoginMember AuthMember member,
        @PathVariable Long cartItemId,
        @Valid @RequestBody ChangeCartItemQuantityRequest request) {
        return CartResponse.Line.from(cartService.changeQuantity(member.id(), cartItemId, request.quantity()));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Void> removeItem(@LoginMember AuthMember member, @PathVariable Long cartItemId) {
        cartService.removeItem(member.id(), cartItemId);
        return ResponseEntity.noContent().build();
    }
}
