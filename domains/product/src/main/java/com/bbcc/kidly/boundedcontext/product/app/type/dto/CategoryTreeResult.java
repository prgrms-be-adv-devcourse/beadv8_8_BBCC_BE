package com.bbcc.kidly.boundedcontext.product.app.type.dto;

import java.util.List;

public record CategoryTreeResult(Long categoryId, String name, boolean leaf, List<CategoryTreeResult> children) {
}
