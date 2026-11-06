package com.bbcc.kidly.boundedcontext.childprofile.domain;

public final class ChildProfilePolicy {

    public static final int AGE_LIMIT = 14;
    public static final int GUARDIAN_MIN_AGE = 19;
    public static final int NAME_MAX_LENGTH = 50;
    public static final String HEIGHT_CM_MIN = "40.0";
    public static final String HEIGHT_CM_MAX = "200.0";
    public static final String WEIGHT_KG_MIN = "2.0";
    public static final String WEIGHT_KG_MAX = "150.0";

    private ChildProfilePolicy() {
    }
}
