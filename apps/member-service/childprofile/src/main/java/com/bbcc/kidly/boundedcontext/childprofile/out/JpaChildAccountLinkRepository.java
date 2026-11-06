package com.bbcc.kidly.boundedcontext.childprofile.out;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildAccountLink;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildAccountLinkRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaChildAccountLinkRepository
    extends JpaRepository<ChildAccountLink, Long>, ChildAccountLinkRepository {
}
