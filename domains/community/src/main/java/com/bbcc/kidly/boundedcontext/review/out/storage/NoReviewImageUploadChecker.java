package com.bbcc.kidly.boundedcontext.review.out.storage;

/**
 * 업로드 확인을 하지 않는다(이름 none). 가짜 자격 증명이라 S3에 닿지 않는 로컬 실행에서 쓴다. 늘 있음으로 본다.
 */
public class NoReviewImageUploadChecker implements ReviewImageUploadChecker {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public Presence check(String imageUrl, String key) {
        return Presence.PRESENT;
    }
}
