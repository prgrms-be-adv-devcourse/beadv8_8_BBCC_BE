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
import com.bbcc.kidly.boundedcontext.childprofile.domain.GrowthRecord;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GrowthRecordRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildProfileService {

    private final ChildProfileRepository childProfileRepository;
    private final GrowthRecordRepository growthRecordRepository;
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
        publishGrowthRecorded(child.getGrowthRecords().getLast());
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

    @Transactional
    public ChildProfileResult update(Long memberId, Long childId, ChildProfileUpdateRequest request) {
        var now = LocalDateTime.now();
        var child = childProfileRepository.getByIdAndMemberId(childId, memberId);
        var recorded = child.update(request.toUpdateInfo(), now);
        publish(child, false, now);
        recorded.ifPresent(this::publishGrowthRecorded);
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

    @Transactional
    public void deleteAll(Long memberId) {
        var now = LocalDateTime.now();
        var children = childProfileRepository.findAllByMemberIdForUpdate(memberId);
        for (var child : children) {
            child.delete(now);
            publish(child, true, now);
        }
        log.info("회원 자녀 전체 삭제: memberId={} count={}", memberId, children.size());
    }

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

    private void publishGrowthRecorded(GrowthRecord record) {
        var saved = growthRecordRepository.save(record);
        eventPublisher.publishEvent(saved.toRecordedEvent());
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
