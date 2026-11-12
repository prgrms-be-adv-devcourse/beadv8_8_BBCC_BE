package com.bbcc.kidly.boundedcontext.member.in.dto;

import com.bbcc.kidly.boundedcontext.member.app.dto.DevMemberSeedResult;

/**
 * @param created 이번 요청이 회원을 만들었으면 true, 같은 key의 회원이 이미 있었으면 false
 */
public record DevMemberSeedResponse(Long memberId, boolean created) {

    public static DevMemberSeedResponse from(DevMemberSeedResult result) {
        return new DevMemberSeedResponse(result.memberId(), result.created());
    }
}
