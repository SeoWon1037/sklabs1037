package com.sklabs1037.sklabs1037.beauty.dao;

import com.sklabs1037.sklabs1037.beauty.vo.BeautyLogVO;
import com.sklabs1037.sklabs1037.beauty.vo.IngredientVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 뷰티 랩 Dao
 * - 메서드 이름 = BeautyLabMapper.xml의 id
 */
@Mapper
public interface BeautyLabDao {

    // 성분 사전 전체 (150개 남짓 → 서버 메모리에 올려두고 비교)
    List<IngredientVO> selectIngredients();

    // 기록 저장
    int insertLog(BeautyLogVO vo);

    // 오늘 사용 횟수 (KIND별)
    int countToday(BeautyLogVO vo);

    // 내 최근 기록 (결과 JSON 없이 목록만)
    List<BeautyLogVO> selectMyLogs(BeautyLogVO vo);

    // 내 기록 하나 (결과 JSON 포함)
    BeautyLogVO selectMyLog(BeautyLogVO vo);
}
