package com.bbcc.kidly.boundedcontext.notification.domain;

import java.util.Set;

public record ProductNameChange(boolean newlyStoppedByAdmin, Set<Long> newlySoldOutOptionIds) {

    public static final ProductNameChange NONE = new ProductNameChange(false, Set.of());
}
