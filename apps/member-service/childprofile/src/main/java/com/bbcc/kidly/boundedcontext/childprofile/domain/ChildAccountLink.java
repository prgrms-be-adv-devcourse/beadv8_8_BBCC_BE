package com.bbcc.kidly.boundedcontext.childprofile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "childprofile", name = "child_account_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildAccountLink extends BaseSoftDeleteEntity {
    @Id
    private Long accountId;

    @Column(nullable = false)
    private Long childProfileId;

    public static ChildAccountLink create(Long accountId, Long childProfileId) {
        var link = new ChildAccountLink();
        link.accountId = accountId;
        link.childProfileId = childProfileId;
        return link;
    }
}
