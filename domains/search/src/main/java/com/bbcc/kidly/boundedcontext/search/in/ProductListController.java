package com.bbcc.kidly.boundedcontext.search.in;

import com.bbcc.kidly.boundedcontext.search.app.ProductListService;
import com.bbcc.kidly.boundedcontext.search.app.dto.ProductListRequest;
import com.bbcc.kidly.boundedcontext.search.in.dto.ProductListItemResponse;
import com.bbcc.kidly.global.response.PageResponse;
import com.bbcc.kidly.shared.auth.MemberHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구매자 상품 목록. 상품의 목록 API와 경로 · 요청 · 응답이 같아, 이 컨트롤러는 apps/search-service에서만 띄운다.
 * 공개 API다. 로그인했으면 검색 이벤트에 회원 ID를 담으려고 Gateway가 붙인 회원 ID 헤더를 읽는다.
 * {@code @LoginMember}는 헤더가 없으면 401이라 쓰지 않는다. 헤더가 없거나 잘못되면 비로그인으로 찾는다.
 * {@code @RequestHeader} 대신 요청에서 읽는다. Swagger에 회원 헤더 입력칸이 생기지 않게 하려는 것이다(밖에서 보낸 값은 Gateway가 지운다).
 */
@RestController
@RequiredArgsConstructor
public class ProductListController {

    private final ProductListService productListService;

    @GetMapping("/api/v1/products")
    public PageResponse<ProductListItemResponse> list(@Valid @ModelAttribute ProductListRequest request,
        HttpServletRequest httpRequest) {
        Long memberId = MemberHeaders.parseMemberId(httpRequest.getHeader(MemberHeaders.MEMBER_ID)).orElse(null);
        return PageResponse.from(productListService.list(request, memberId).map(ProductListItemResponse::from));
    }
}
