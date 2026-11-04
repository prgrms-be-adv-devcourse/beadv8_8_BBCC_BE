package com.bbcc.kidly.boundedcontext.childprofile.app;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildAccountLink;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildAccountLinkRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChildAccountLinkService {
    private final ChildAccountLinkRepository repository;

    @Transactional
    public void applyAccountChange(Long accountId, Long childId, boolean deleted, LocalDateTime occurredAt) {
        var link = repository.findById(accountId).orElseGet(() -> ChildAccountLink.create(accountId, childId));
        if (deleted) {
            link.delete(occurredAt);
        }
        repository.save(link);
    }
}
