package com.bbcc.kidly.boundedcontext.order.app.order;

import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

/**
 * 같은 주문 요청인지 가리는 비교값(#351). items를 optionId → childId(null 먼저) 순으로 정렬해 (optionId · childId ·
 * quantity · unitPrice)와 배송지 4칸을 이은 뒤 SHA-256 소문자 16진수 64자로 만든다. items 순서는 보지 않는다.
 * 값마다 "길이:값"으로 이어 붙여, 값 안에 구분자가 있어도 칸 경계가 다른 두 요청이 같은 문자열이 되지 않게 한다.
 */
final class OrderRequestFingerprint {

    private static final Comparator<PlaceOrderCommand.Item> ITEM_ORDER = Comparator
        .comparing(PlaceOrderCommand.Item::optionId, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(PlaceOrderCommand.Item::childId, Comparator.nullsFirst(Comparator.naturalOrder()));

    private OrderRequestFingerprint() {
    }

    static String of(PlaceOrderCommand command) {
        StringBuilder canonical = new StringBuilder();
        command.items().stream().sorted(ITEM_ORDER).forEach(item -> {
            append(canonical, item.optionId());
            append(canonical, item.childId());
            append(canonical, item.quantity());
            append(canonical, item.unitPrice());
        });
        PlaceOrderCommand.ShippingAddress address = command.shippingAddress();
        append(canonical, address.recipientName());
        append(canonical, address.phone());
        append(canonical, address.zipCode());
        append(canonical, address.address());
        return HexFormat.of().formatHex(sha256(canonical.toString()));
    }

    // null은 길이 -1로 적어 빈 문자열과 구분한다
    private static void append(StringBuilder canonical, Object value) {
        if (value == null) {
            canonical.append("-1:");
            return;
        }
        String text = value.toString();
        canonical.append(text.length()).append(':').append(text);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }
}
