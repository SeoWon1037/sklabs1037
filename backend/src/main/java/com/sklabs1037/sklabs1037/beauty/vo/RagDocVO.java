package com.sklabs1037.sklabs1037.beauty.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 규제 Q&A 문서 VO
 * - 사용 테이블: T_RAG_DOC
 */
@Getter
@Setter
public class RagDocVO {

    private Long docId;
    private String title;           // 문서 이름
    private String source;          // 출처
    private String fileName;
    private String status;          // PROCESSING / READY / FAIL
    private Integer pageCount;
    private Integer chunkCount;
    private String message;
    private Long userId;
    private OffsetDateTime insDay;
    private OffsetDateTime updDay;
}
