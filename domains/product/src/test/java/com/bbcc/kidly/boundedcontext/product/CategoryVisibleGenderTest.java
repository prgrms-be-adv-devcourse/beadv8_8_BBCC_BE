package com.bbcc.kidly.boundedcontext.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.product.domain.type.Category;
import com.bbcc.kidly.boundedcontext.product.domain.type.ProductType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.product.domain.CategoryVisibleGender;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CategoryVisibleGenderTest {

    private final ProductType clothingType = ProductType.define("의류", ProductTypeFixture.clothingLinks());
    private final Category clothing = CategoryFixture.clothing();

    @Test
    @DisplayName("VIS-01 보일 성별을 정하지 않았거나 null로 정하면 ALL이다")
    void visibleGenderIsAllWhenAbsent() {
        Category top = CategoryFixture.top(clothing, clothingType);
        Category dress = CategoryFixture.top(clothing, clothingType);
        dress.changeVisibleGender(CategoryVisibleGender.GIRL);
        dress.changeVisibleGender(null);

        assertThat(List.of(clothing, top, dress))
            .allSatisfy(category -> assertThat(category.getVisibleGender()).isEqualTo(CategoryVisibleGender.ALL));
    }

    @Test
    @DisplayName("VIS-01 보일 성별을 GIRL로 정하면 GIRL이다")
    void visibleGenderIsGirlWhenSet() {
        Category dress = CategoryFixture.top(clothing, clothingType);

        dress.changeVisibleGender(CategoryVisibleGender.GIRL);

        assertThat(dress.getVisibleGender()).isEqualTo(CategoryVisibleGender.GIRL);
    }

    @Test
    @DisplayName("VIS-02 남아 자녀에게는 GIRL 카테고리를 보여 주지 않는다")
    void girlCategoryIsHiddenFromBoy() {
        Category dress = CategoryFixture.top(clothing, clothingType);
        dress.changeVisibleGender(CategoryVisibleGender.GIRL);

        assertThat(dress.isVisibleTo(Gender.MALE)).isFalse();
    }

    @ParameterizedTest(name = "{0} 카테고리, {1} 자녀")
    @CsvSource({"ALL, MALE", "ALL, FEMALE", "GIRL, FEMALE"})
    @DisplayName("VIS-02 남아 자녀에게는 ALL 카테고리를, 여아 자녀에게는 모든 카테고리를 보여 준다")
    void categoryIsVisibleByChildGender(CategoryVisibleGender visibleGender, Gender childGender) {
        Category category = CategoryFixture.top(clothing, clothingType);
        category.changeVisibleGender(visibleGender);

        assertThat(category.isVisibleTo(childGender)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(CategoryVisibleGender.class)
    @DisplayName("VIS-03 자녀를 고르지 않아 성별이 없으면 모든 카테고리를 보여 준다")
    void categoryIsVisibleWithoutChildGender(CategoryVisibleGender visibleGender) {
        Category category = CategoryFixture.top(clothing, clothingType);
        category.changeVisibleGender(visibleGender);

        assertThat(category.isVisibleTo(null)).isTrue();
    }
}
