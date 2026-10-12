package com.bbcc.kidly.boundedcontext.member.in.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record DevTokenRequest(
    @NotNull Long memberId,
    @NotEmpty List<@NotNull @Pattern(regexp = "BUYER|SELLER|ADMIN") String> roles) {
}
