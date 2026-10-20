package com.sklabs1037.sklabs1037.beauty.dao;

import com.sklabs1037.sklabs1037.beauty.vo.RagChunkVO;
import com.sklabs1037.sklabs1037.beauty.vo.RagDocVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 규제 Q&A Dao
 * - 메서드 이름 = RegulationMapper.xml의 id
 */
@Mapper
public interface RegulationDao {

    // 문서 등록 (PROCESSING) → docId 채워짐
    int insertDoc(RagDocVO vo);

    // 문서 상태·진행 갱신
    int updateDoc(RagDocVO vo);

    // 문서 목록
    List<RagDocVO> selectDocs();

    // 문서 삭제 (문단은 CASCADE)
    int deleteDoc(Long docId);

    // 문단 저장
    int insertChunk(RagChunkVO vo);

    // 질문 벡터와 가까운 문단 (READY 문서만)
    List<RagChunkVO> searchChunks(RagChunkVO vo);

    // 서버가 꺼지면서 PROCESSING으로 남은 문서 → FAIL
    int failStaleDocs();
}
