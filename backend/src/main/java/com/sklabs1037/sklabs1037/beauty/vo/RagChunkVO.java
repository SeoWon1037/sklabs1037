package com.sklabs1037.sklabs1037.beauty.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 규제 Q&A 문단(청크) VO
 * - 사용 테이블: T_RAG_CHUNK (+ T_RAG_DOC)
 */
@Getter
@Setter
public class RagChunkVO {

    private Long chunkId;
    private Long docId;
    private Integer seq;
    private Integer page;
    private String content;
    private String embedding;       // "[0.1,0.2,...]" (pgvector 글자 형식)

    // 검색 결과
    private String title;           // 문서 이름
    private String source;
    private Double score;           // 코사인 유사도 (1 - 거리, 1에 가까울수록 비슷)

    // 검색 조건
    private Integer limit;
}
