package com.sklabs1037.sklabs1037.idea.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 💡 기능 제안 + 투표 DAO (SQL: resources/mapper/IdeaMapper.xml)
 */
@Mapper
public interface IdeaDao {

    // sort: TOP(투표순) / NEW(최신순), status: null이면 전부
    List<Map<String, Object>> selectIdeas(@Param("userId") Long userId, @Param("sort") String sort,
                                          @Param("status") String status, @Param("limit") int limit);

    List<Map<String, Object>> selectStatusCounts();

    // 비슷한 제안 찾기 후보 (보류 빼고 최근·인기 순)
    List<Map<String, Object>> selectCandidates(@Param("limit") int limit);

    Map<String, Object> selectIdea(@Param("id") Long id);

    int countToday(@Param("userId") Long userId);

    int insertIdea(Map<String, Object> idea);   // useGeneratedKeys → idea.id

    int deleteIdea(@Param("id") Long id, @Param("userId") Long userId);

    int insertVote(@Param("ideaId") Long ideaId, @Param("userId") Long userId);

    int deleteVote(@Param("ideaId") Long ideaId, @Param("userId") Long userId);

    int addVoteCount(@Param("ideaId") Long ideaId, @Param("delta") int delta);

    int selectVoteCount(@Param("ideaId") Long ideaId);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("note") String note);

    List<Long> selectVoters(@Param("ideaId") Long ideaId);
}
