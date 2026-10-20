package com.sklabs1037.sklabs1037.ops.dao;

import com.sklabs1037.sklabs1037.ops.vo.JobRunVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 작업 실행 기록 Dao
 * - 메서드 이름 = JobRunMapper.xml의 id
 */
@Mapper
public interface JobRunDao {

    // 시작 기록 (RUNNING) → runId 채워짐
    int insertRun(JobRunVO vo);

    // 끝 기록 (SUCCESS / PARTIAL / FAIL)
    int finishRun(JobRunVO vo);

    // 이미 끝난 기록 한 번에 (1분 작업의 실패 등)
    int insertFinishedRun(JobRunVO vo);

    // 서버가 꺼지면서 RUNNING으로 남은 기록 → ABORTED
    int abortStaleRuns(JobRunVO vo);

    // 최근 실행 목록
    List<JobRunVO> selectRecentRuns(JobRunVO vo);

    // 작업별 마지막 실행 1줄씩
    List<JobRunVO> selectLatestPerJob();

    // 이 시각 이후 성공(SUCCESS / PARTIAL)한 횟수
    int countSuccessSince(JobRunVO vo);

    // 오래된 기록 삭제
    int deleteOldRuns(JobRunVO vo);

    // 데이터 상태 (종목·시세·뉴스·요약·알림·주문)
    JobRunVO selectDataHealth();

    // 관리자 메일 주소
    List<String> selectAdminEmails();
}
