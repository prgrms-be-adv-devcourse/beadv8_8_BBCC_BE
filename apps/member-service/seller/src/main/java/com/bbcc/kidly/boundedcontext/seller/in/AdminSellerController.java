package com.bbcc.kidly.boundedcontext.seller.in;

import com.bbcc.kidly.boundedcontext.seller.app.SellerChangedRepublishService;
import com.bbcc.kidly.boundedcontext.seller.app.SellerService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerSuspendRequest;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerChangedRepublishResponse;
import com.bbcc.kidly.boundedcontext.seller.in.dto.SellerResponse;
import com.bbcc.kidly.member.support.security.AuthAdmin;
import com.bbcc.kidly.member.support.security.LoginAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/sellers")
@RequiredArgsConstructor
public class AdminSellerController {

    private final SellerService sellerService;
    private final SellerChangedRepublishService sellerChangedRepublishService;

    @PostMapping("/{sellerId}/suspend")
    public SellerResponse suspend(@LoginAdmin AuthAdmin admin, @PathVariable Long sellerId,
        @Valid @RequestBody SellerSuspendRequest request) {
        return SellerResponse.from(sellerService.suspend(sellerId, admin.id(), request));
    }

    @PostMapping("/changed-events")
    public SellerChangedRepublishResponse republishChangedEvents() {
        return new SellerChangedRepublishResponse(sellerChangedRepublishService.republishAll());
    }
}
