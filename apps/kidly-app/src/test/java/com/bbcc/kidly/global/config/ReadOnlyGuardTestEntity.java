package com.bbcc.kidly.global.config;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 통합 테스트용: 읽기 전용 트랜잭션의 쓰기 검사를 확인할 때 쓰는 엔티티다.
 * ID가 IDENTITY라 저장하는 순간 INSERT가 나가서, flush를 기다리지 않는 쓰기를 확인할 수 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ReadOnlyGuardTestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    ReadOnlyGuardTestEntity(String name) {
        this.name = name;
    }
}
