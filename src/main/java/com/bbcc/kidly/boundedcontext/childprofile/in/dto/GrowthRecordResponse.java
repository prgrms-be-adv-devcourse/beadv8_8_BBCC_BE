package com.bbcc.kidly.boundedcontext.childprofile.in.dto;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.GrowthRecordResult;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param bodyType 기록 당시 체형. 입력하지 않았으면 null
 */
public record GrowthRecordResponse(Long id, BigDecimal heightCm, BodyType bodyType, LocalDateTime recordedAt) {

    public static GrowthRecordResponse from(GrowthRecordResult result) {
        return new GrowthRecordResponse(result.id(), result.heightCm(), result.bodyType(), result.recordedAt());
    }
}
