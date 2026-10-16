package com.bbcc.kidly.boundedcontext.setting.domain;

import com.bbcc.kidly.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FE 동작 설정. 서비스 전체에 하나뿐이라 ID가 항상 {@link #ID}다.
 * 바꾸기 전 기본값은 목 데이터 켬이다 (FE 빌드 값의 기본값과 같다).
 */
@Entity
@Table(schema = "setting", name = "app_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSetting extends BaseTimeEntity {

    public static final Long ID = 1L;

    @Id
    private Long id;

    // 백엔드에 없는 API를 FE 목 응답으로 대신한다
    @Column(name = "is_mock_enabled", nullable = false)
    private boolean mockEnabled;

    private AppSetting(boolean mockEnabled) {
        this.id = ID;
        this.mockEnabled = mockEnabled;
    }

    public static AppSetting defaults() {
        return new AppSetting(true);
    }

    public void change(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
    }
}
