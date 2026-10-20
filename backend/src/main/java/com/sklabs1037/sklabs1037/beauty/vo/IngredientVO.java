package com.sklabs1037.sklabs1037.beauty.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 화장품 성분 사전 VO
 * - 사용 테이블: T_INGREDIENT
 */
@Getter
@Setter
public class IngredientVO {

    private Long ingredientId;
    private String nameKo;          // 한글 성분명
    private String nameEn;          // INCI 영문명
    private String aliases;         // 다른 표기 (쉼표 구분)
    private String category;        // 역할 (보습, 진정, 방부제 ...)
    private String description;     // 쉬운 설명
    private String functional;      // 기능성 고시 성분: 미백 / 주름 / 자외선차단 / 여드름 / 탈모
    private String allergenYn;      // 알레르기 유발 가능 착향제 25종
    private String caution;         // 주의할 점
    private String cautionSkin;     // 주의 피부 (쉼표 구분)
}
