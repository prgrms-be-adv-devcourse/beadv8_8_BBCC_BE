package com.bbcc.kidly.boundedcontext.favorite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지운 자녀 계정(child-account.changed deleted=true를 받은 계정). 계정 ID는 다시 쓰지 않으므로 한 번 들어오면 바뀌지 않는다.
 * 그 계정의 찜 요청(토큰 만료 전 · 삭제 이벤트보다 늦게 온 요청)을 막는 데 쓴다. 넣기는 저장소의 INSERT … ON CONFLICT DO NOTHING이 한다.
 */
@Entity(name = "CommunityDeletedChildAccount")
@Table(schema = "community", name = "deleted_child_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeletedChildAccount {

    @Id
    private Long accountId;

    @Column(nullable = false)
    private Long guardianId;

    @Column(nullable = false)
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
