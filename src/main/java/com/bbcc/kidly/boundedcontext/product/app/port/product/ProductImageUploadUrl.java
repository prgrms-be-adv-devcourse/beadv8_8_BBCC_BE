package com.bbcc.kidly.boundedcontext.product.app.port.product;

import java.time.Instant;

public record ProductImageUploadUrl(String uploadUrl, String imageUrl, Instant expiresAt) {
}
