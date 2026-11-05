package com.bbcc.kidly.boundedcontext.childprofile.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VerifiedIdentity(String name, String phoneNumber, LocalDate birthDate, LocalDateTime verifiedAt) {
}
