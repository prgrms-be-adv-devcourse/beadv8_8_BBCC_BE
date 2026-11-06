package com.bbcc.kidly.boundedcontext.search.in;

import com.bbcc.kidly.boundedcontext.search.app.SearchClickService;
import com.bbcc.kidly.boundedcontext.search.app.dto.SearchClickRequest;
import com.bbcc.kidly.global.security.AuthMember;
import com.bbcc.kidly.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색 결과 클릭 기록(#1004). 로그인 회원이 검색 결과에서 상품을 누를 때 FE가 부른다.
 */
@RestController
@RequiredArgsConstructor
public class SearchClickController {

    private final SearchClickService searchClickService;

    // 회원을 본문보다 먼저 읽어, 로그인하지 않았으면 본문이 잘못돼도 401이다
    @PostMapping("/api/v1/search/clicks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@LoginMember AuthMember member, @Valid @RequestBody SearchClickRequest request) {
        searchClickService.record(request, member.id());
    }
}
