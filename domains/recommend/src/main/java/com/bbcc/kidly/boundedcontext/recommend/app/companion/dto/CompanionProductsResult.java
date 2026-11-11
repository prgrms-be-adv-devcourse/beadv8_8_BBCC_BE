package com.bbcc.kidly.boundedcontext.recommend.app.companion.dto;

import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionSource;
import java.util.List;

public record CompanionProductsResult(CompanionSource source, List<CompanionProductResult> products) {
}
