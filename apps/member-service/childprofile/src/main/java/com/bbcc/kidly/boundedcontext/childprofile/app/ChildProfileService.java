package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileUpdateRequest;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileResult;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.GrowthRecordResult;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfile;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildAccountLink;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildAccountLinkRepository;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerificationRepository;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileErrorCode;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자의 자녀 프로필을 등록 · 조회 · 수정 · 삭제한다. 본인 자녀만 다루고, 아니면 404다.
 * 등록 · 수정 때는 바뀐 뒤 전체 값으로, 삭제 때는 삭제 직전 값에 {@code deleted = true}를 붙여 {@link ChildChangedEvent}를 발행한다.
 * 추천 · 주문이 복사본을 갱신한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildProfileService {

    private final ChildProfileRepository childProfileRepository;
    private final ChildAccountLinkRepository childAccountLinkRepository;
    private final GuardianVerificationRepository guardianVerificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChildProfileResult register(Long memberId, ChildProfileRequest request) {
        if (!guardianVerificationRepository.existsByMemberId(memberId)) {
            throw new BusinessException(ChildProfileErrorCode.VERIFICATION_REQUIRED);
        }
        var now = LocalDateTime.now();
        var child = childProfileRepository.save(ChildProfile.register(request.toRegisterInfo(memberId), now));
        publish(child, false, now);
        log.info("자녀 등록: memberId={} childId={}", memberId, child.getId());
        return ChildProfileResult.from(child);
    }

    @Transactional(readOnly = true)
    public List<ChildProfileResult> getChildren(Long memberId) {
        return childProfileRepository.findAllByMemberId(memberId).stream().map(ChildProfileResult::from).toList();
    }

    @Transactional(readOnly = true)
    public ChildProfileResult getChild(Long memberId, Long childId) {
        return ChildProfileResult.from(childProfileRepository.getByIdAndMemberId(childId, memberId));
    }

    @Transactional(readOnly = true)
    public ChildProfileResult getOwnProfile(Long accountId, Long guardianId) {
        return ChildProfileResult.from(getOwnChild(accountId, guardianId));
    }

    @Transactional(readOnly = true)
    public boolean isNoticeConfirmed(Long accountId, Long guardianId) {
        return getOwnChild(accountId, guardianId).isNoticeConfirmed();
    }

    @Transactional
    public void confirmNotice(Long accountId, Long guardianId) {
        getOwnChild(accountId, guardianId).confirmNotice(LocalDateTime.now());
    }

    private ChildProfile getOwnChild(Long accountId, Long guardianId) {
        var childId = childAccountLinkRepository.findById(accountId)
            .filter(link -> !link.isDeleted())
            .map(ChildAccountLink::getChildProfileId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return childProfileRepository.getByIdAndMemberId(childId, guardianId);
    }

    // 이름만 바뀌어도 이벤트를 발행한다. 받는 쪽은 어떤 필드가 바뀌었는지 보지 않고 덮어쓴다
    @Transactional
    public ChildProfileResult update(Long memberId, Long childId, ChildProfileUpdateRequest request) {
        var now = LocalDateTime.now();
        var child = childProfileRepository.getByIdAndMemberId(childId, memberId);
        child.update(request.toUpdateInfo(), now);
        publish(child, false, now);
        log.info("자녀 수정: memberId={} childId={}", memberId, childId);
        return ChildProfileResult.from(child);
    }

    @Transactional
    public void delete(Long memberId, Long childId) {
        var now = LocalDateTime.now();
        var child = childProfileRepository.getByIdAndMemberId(childId, memberId);
        child.delete(now);
        publish(child, true, now);
        log.info("자녀 삭제: memberId={} childId={}", memberId, childId);
    }

    // 회원이 탈퇴하면 자녀를 모두 삭제한다. 자녀마다 삭제 이벤트를 발행한다
    @Transactional
    public void deleteAll(Long memberId) {
        var now = LocalDateTime.now();
        var children = childProfileRepository.findAllByMemberId(memberId);
        for (var child : children) {
            child.delete(now);
            publish(child, true, now);
        }
        log.info("탈퇴 회원 자녀 삭제: memberId={} count={}", memberId, children.size());
    }

    // 본인인증 기록까지 지워야 같은 사람이 다시 가입해 인증할 수 있다
    @Transactional
    public void deleteWithdrawnMember(Long memberId) {
        deleteAll(memberId);
        guardianVerificationRepository.deleteByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public List<GrowthRecordResult> getGrowthRecords(Long memberId, Long childId) {
        return childProfileRepository.getByIdAndMemberId(childId, memberId).getGrowthRecords().stream()
            .map(GrowthRecordResult::from)
            .toList();
    }

    private void publish(ChildProfile child, boolean deleted, LocalDateTime now) {
        eventPublisher.publishEvent(new ChildChangedEvent(
            UUID.randomUUID(),
            now,
            new ChildChangedEvent.Data(
                child.getId(), child.getMemberId(), child.getHeightCm(), child.getWeightKg(), child.getGender(),
                child.getBirthDate(), child.getBodyType(), child.getPreferredFit(), deleted)));
    }
}
