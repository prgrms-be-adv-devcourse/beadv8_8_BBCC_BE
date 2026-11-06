package com.bbcc.kidly.boundedcontext.cart.in.web;

import com.bbcc.kidly.boundedcontext.cart.app.CartService;
import com.bbcc.kidly.boundedcontext.cart.app.dto.AddCartItemResult;
import com.bbcc.kidly.boundedcontext.cart.app.port.CartActors.Actor;
import com.bbcc.kidly.boundedcontext.cart.in.web.dto.AddCartItemRequest;
import com.bbcc.kidly.boundedcontext.cart.in.web.dto.CartResponse;
import com.bbcc.kidly.boundedcontext.cart.in.web.dto.ChangeCartItemQuantityRequest;
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
 * 장바구니 API. 부른 사람(회원 또는 로그인한 자녀, #998)의 "내 장바구니"만 다룬다(@CartCaller). 남의 줄은 같은 가족이면 403,
 * 그 밖이면 404. 예약된 줄은 빼거나 수량을 바꾸면 409.
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
        @CartCaller Actor owner,
        @Valid @RequestBody AddCartItemRequest request) {
        AddCartItemResult result = cartService.addItem(
            owner, request.optionId(), request.childId(), request.quantity());
        CartResponse.Line body = CartResponse.Line.from(result.line());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create(CART_ITEMS_PATH + "/" + body.cartItemId())).body(body);
    }

    @GetMapping
    public CartResponse getCart(@CartCaller Actor owner) {
        return CartResponse.from(cartService.getCart(owner));
    }

    @PatchMapping("/items/{cartItemId}")
    public CartResponse.Line changeQuantity(
        @CartCaller Actor owner,
        @PathVariable Long cartItemId,
        @Valid @RequestBody ChangeCartItemQuantityRequest request) {
        return CartResponse.Line.from(cartService.changeQuantity(owner, cartItemId, request.quantity()));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Void> removeItem(@CartCaller Actor owner, @PathVariable Long cartItemId) {
        cartService.removeItem(owner, cartItemId);
        return ResponseEntity.noContent().build();
    }
}
