package com.bbcc.kidly.boundedcontext.childprofile.domain;

import com.bbcc.kidly.global.entity.BaseSoftDeleteEntity;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 자녀 프로필. 보호자(memberId)는 바뀌지 않는다. 등록할 때와 키 · 체형이 바뀔 때 성장 이력이 1건 쌓인다.
 * 삭제는 논리 삭제이고, 삭제된 자녀는 모든 조회에서 빠진다.
 */
@Entity
@Table(
    name = "child_profile",
    schema = "childprofile",
    indexes = @Index(name = "idx_child_profile_member_id", columnList = "member_id"))
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildProfile extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = ChildProfilePolicy.NAME_MAX_LENGTH)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(name = "height_cm", nullable = false, precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", nullable = false, precision = 4, scale = 1)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type")
    private BodyType bodyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_fit")
    private PreferredFit preferredFit;

    // 단방향(@JoinColumn)이면 이력 1건을 추가할 때 기존 이력을 전부 불러오므로 mappedBy로 둔다
    @OneToMany(mappedBy = "childProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("recordedAt ASC, id ASC")
    private List<GrowthRecord> growthRecords = new ArrayList<>();

    private ChildProfile(Long memberId) {
        this.memberId = memberId;
    }

    public static ChildProfile register(ChildProfileRegisterInfo info, LocalDateTime now) {
        var child = new ChildProfile(info.memberId());
        child.change(info.name(), info.birthDate(), info.gender(), info.heightCm(), info.weightKg(),
            info.bodyType(), info.preferredFit());
        child.growthRecords.add(new GrowthRecord(child, info.heightCm(), info.bodyType(), now));
        return child;
    }

    // 모든 필드를 받는 전체 교체다. 키(값 비교) 또는 체형(null ↔ 값 포함)이 바뀌면 성장 이력을 1건 추가한다
    public void update(ChildProfileUpdateInfo info, LocalDateTime now) {
        boolean grown = this.heightCm.compareTo(info.heightCm()) != 0
            || !Objects.equals(this.bodyType, info.bodyType());
        change(info.name(), info.birthDate(), info.gender(), info.heightCm(), info.weightKg(),
            info.bodyType(), info.preferredFit());
        if (grown) {
            growthRecords.add(new GrowthRecord(this, info.heightCm(), info.bodyType(), now));
        }
    }

    public int growthRecordCount() {
        return growthRecords.size();
    }

    public LocalDateTime lastRecordedAt() {
        return growthRecords.getLast().getRecordedAt();
    }

    public List<GrowthRecord> getGrowthRecords() {
        return Collections.unmodifiableList(growthRecords);
    }

    private void change(String name, LocalDate birthDate, Gender gender, BigDecimal heightCm, BigDecimal weightKg,
        BodyType bodyType, PreferredFit preferredFit) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.bodyType = bodyType;
        this.preferredFit = preferredFit;
    }
}
