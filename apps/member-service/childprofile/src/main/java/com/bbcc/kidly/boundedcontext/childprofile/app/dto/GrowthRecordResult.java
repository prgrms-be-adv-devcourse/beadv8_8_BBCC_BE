package com.bbcc.kidly.boundedcontext.childprofile.app.dto;

import com.bbcc.kidly.boundedcontext.childprofile.domain.GrowthRecord;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GrowthRecordResult(Long id, BigDecimal heightCm, BodyType bodyType, LocalDateTime recordedAt) {

    public static GrowthRecordResult from(GrowthRecord record) {
        return new GrowthRecordResult(record.getId(), record.getHeightCm(), record.getBodyType(),
            record.getRecordedAt());
    }
}
