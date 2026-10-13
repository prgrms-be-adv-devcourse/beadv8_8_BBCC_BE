package com.bbcc.kidly.boundedcontext.seller.domain;

import static java.util.Objects.requireNonNull;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import com.bbcc.kidly.shared.seller.domain.SellerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * 판매자. {@code id}는 회원 ID와 같아서(sellerId = memberId) 회원 1명당 최대 1개다.
 * 승인된 신청에서만 만든다.
 */
@Entity
@Table(name = "seller", schema = "seller")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller extends BaseTimeEntity {

    @Id
    private Long id;

    @Column(nullable = false, length = SellerApplication.NAME_MAX_LENGTH)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status;

    // id를 직접 넣으므로 Spring Data는 version이 null인지로 새 판매자를 판단한다. 새 판매자는 persist되어 같은 회원이면 PK 충돌로 실패한다
    @Version
    private Long version;

    private Seller(Long id, String businessName) {
        this.id = id;
        this.businessName = businessName;
        this.status = SellerStatus.ACTIVE;
    }

    public static Seller from(SellerApplication application) {
        requireNonNull(application, "application은 필수입니다");
        Assert.state(application.isApproved(), "승인된 신청으로만 판매자를 만들 수 있습니다");
        return new Seller(application.getApplicantId(), application.getBusinessName());
    }
}
