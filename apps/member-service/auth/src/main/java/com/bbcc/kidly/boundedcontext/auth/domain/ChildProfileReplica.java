package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.member.support.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "child_profile_replica", schema = "auth")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildProfileReplica extends BaseTimeEntity {

    @Id
    private Long id;

    @Column(name = "guardian_id", nullable = false)
    private Long guardianId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
