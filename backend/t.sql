SET search_path TO bt;
INSERT INTO T_USER(ID,NICKNAME) VALUES (901,'호스트'),(902,'게스트') ON CONFLICT DO NOTHING;
INSERT INTO T_ATTENDANCE(USER_ID,DAY) VALUES (901,'2026-10-13'),(901,'2026-10-14'),(902,'2026-10-14') ON CONFLICT DO NOTHING;
INSERT INTO T_NEWS_QUIZ(QUIZ_DATE,QUESTIONS_JSON,NEWS_COUNT) VALUES ('2026-10-14','[1,2,3,4,5]',10) ON CONFLICT DO NOTHING;
INSERT INTO T_NEWS_QUIZ_PLAY(QUIZ_DATE,USER_ID,SCORE,ANSWERS_JSON) VALUES ('2026-10-14',901,4,'[]') ON CONFLICT DO NOTHING;
INSERT INTO T_MOCK_TRADE(USER_ID,INS_DAY) VALUES (901,'2026-10-18 23:30:00+09'),(901,'2026-10-19 00:10:00+09');
INSERT INTO T_AI_CHAT_LOG(USER_ID) VALUES (901);
SELECT  (SELECT COUNT(*) FROM T_MOCK_TRADE T WHERE T.USER_ID = 901 AND T.INS_DAY >= ('2026-10-12'::DATE::TIMESTAMP AT TIME ZONE 'Asia/Seoul') AND T.INS_DAY < (('2026-10-18'::DATE::DATE + 1)::TIMESTAMP AT TIME ZONE 'Asia/Seoul'))::INT AS "trades"
               ,(SELECT COUNT(*)
                   FROM T_PREDICT_VOTE V
                   JOIN T_PREDICT_ITEM I ON I.ID = V.ITEM_ID
                  WHERE V.USER_ID = 901
                    AND I.TRADE_DATE BETWEEN '2026-10-12'::DATE AND '2026-10-18'::DATE)::INT                                    AS "predictVotes"
               ,(SELECT COUNT(*)
                   FROM T_PREDICT_VOTE V
                   JOIN T_PREDICT_ITEM I ON I.ID = V.ITEM_ID
                  WHERE V.USER_ID = 901
                    AND V.CORRECT IS NOT NULL
                    AND I.TRADE_DATE BETWEEN '2026-10-12'::DATE AND '2026-10-18'::DATE)::INT                                    AS "predictGraded"
               ,(SELECT COUNT(*)
                   FROM T_PREDICT_VOTE V
                   JOIN T_PREDICT_ITEM I ON I.ID = V.ITEM_ID
                  WHERE V.USER_ID = 901
                    AND V.CORRECT = TRUE
                    AND I.TRADE_DATE BETWEEN '2026-10-12'::DATE AND '2026-10-18'::DATE)::INT                                    AS "predictHits"
               ,(SELECT COUNT(*) FROM T_NEWS_QUIZ_PLAY P
                  WHERE P.USER_ID = 901 AND P.QUIZ_DATE BETWEEN '2026-10-12'::DATE AND '2026-10-18'::DATE)::INT          AS "quizPlays"
               ,(SELECT COALESCE(SUM(P.SCORE), 0) FROM T_NEWS_QUIZ_PLAY P
                  WHERE P.USER_ID = 901 AND P.QUIZ_DATE BETWEEN '2026-10-12'::DATE AND '2026-10-18'::DATE)::INT          AS "quizScore"
               ,(SELECT COUNT(*) FROM T_STOCK_POST S
                  WHERE S.USER_ID = 901 AND S.DEL_DAY IS NULL AND S.INS_DAY >= ('2026-10-12'::DATE::TIMESTAMP AT TIME ZONE 'Asia/Seoul') AND S.INS_DAY < (('2026-10-18'::DATE::DATE + 1)::TIMESTAMP AT TIME ZONE 'Asia/Seoul'))::INT            AS "boardPosts"
               ,(SELECT COUNT(*) FROM T_STOCK_COMMENT C
                  WHERE C.USER_ID = 901 AND C.DEL_DAY IS NULL AND C.INS_DAY >= ('2026-10-12'::DATE::TIMESTAMP AT TIME ZONE 'Asia/Seoul') AND C.INS_DAY < (('2026-10-18'::DATE::DATE + 1)::TIMESTAMP AT TIME ZONE 'Asia/Seoul'))::INT            AS "comments"
               ,(SELECT COUNT(*) FROM T_ATTENDANCE A
                  WHERE A.USER_ID = 901 AND A.DAY BETWEEN '2026-10-12'::DATE AND '2026-10-18'::DATE)::INT                AS "attendDays"
               ,(SELECT COUNT(*) FROM T_AI_CHAT_LOG L WHERE L.USER_ID = 901 AND L.INS_DAY >= ('2026-10-12'::DATE::TIMESTAMP AT TIME ZONE 'Asia/Seoul') AND L.INS_DAY < (('2026-10-18'::DATE::DATE + 1)::TIMESTAMP AT TIME ZONE 'Asia/Seoul'))::INT AS "aiChats"
               ,(SELECT COUNT(*) FROM T_BEAUTY_LOG B WHERE B.USER_ID = 901 AND B.INS_DAY >= ('2026-10-12'::DATE::TIMESTAMP AT TIME ZONE 'Asia/Seoul') AND B.INS_DAY < (('2026-10-18'::DATE::DATE + 1)::TIMESTAMP AT TIME ZONE 'Asia/Seoul'))::INT  AS "beautyLogs";
SELECT USER_ID FROM (
            SELECT A.USER_ID FROM T_ATTENDANCE A WHERE A.DAY BETWEEN '2026-10-12'::DATE AND '2026-10-18'::DATE
            UNION
            SELECT P.USER_ID FROM T_NEWS_QUIZ_PLAY P WHERE P.QUIZ_DATE BETWEEN '2026-10-12'::DATE AND '2026-10-18'::DATE
            UNION
            SELECT V.USER_ID FROM T_PREDICT_VOTE V JOIN T_PREDICT_ITEM I ON I.ID = V.ITEM_ID WHERE I.TRADE_DATE BETWEEN '2026-10-12'::DATE AND '2026-10-18'::DATE
            UNION
            SELECT T.USER_ID FROM T_MOCK_TRADE T WHERE T.INS_DAY >= ('2026-10-12'::DATE::TIMESTAMP AT TIME ZONE 'Asia/Seoul') AND T.INS_DAY < (('2026-10-18'::DATE::DATE + 1)::TIMESTAMP AT TIME ZONE 'Asia/Seoul')
        ) X
        JOIN T_USER U ON U.ID = X.USER_ID AND U.DEL_DAY IS NULL
        ORDER BY USER_ID
        LIMIT  10;
INSERT INTO T_WEEKLY_COACH (USER_ID, WEEK_START, STATS_JSON, REPORT_JSON)
        VALUES (901, '2026-10-19'::DATE, '{}', '{"a":1}')
        ON CONFLICT (USER_ID, WEEK_START) DO NOTHING;
INSERT INTO T_WEEKLY_COACH (USER_ID, WEEK_START, STATS_JSON, REPORT_JSON)
        VALUES (901, '2026-10-19'::DATE, '{}', '{"a":1}')
        ON CONFLICT (USER_ID, WEEK_START) DO NOTHING;
SELECT  STATS_JSON                                      AS "statsJson"
               ,REPORT_JSON                                     AS "reportJson"
               ,(EXTRACT(EPOCH FROM INS_DAY) * 1000)::BIGINT    AS "insAt"
          FROM T_WEEKLY_COACH
         WHERE 1=1
           AND USER_ID    = 901
           AND WEEK_START = '2026-10-19'::DATE;
INSERT INTO T_SHARE_CARD (CODE, USER_ID, DATA_JSON)
        VALUES ('abcDEF2345', 901, '{}');
SELECT COUNT(*)
          FROM T_SHARE_CARD
         WHERE 1=1
           AND USER_ID = 901
           AND INS_DAY >= NOW() - INTERVAL '1 day';
SELECT DATA_JSON FROM T_SHARE_CARD WHERE CODE = 'abcDEF2345';
INSERT INTO T_DUEL (CODE, HOST_ID)
        VALUES ('duelcode01', 901);
SELECT COUNT(*)
          FROM T_DUEL
         WHERE 1=1
           AND (HOST_ID = 901 OR GUEST_ID = 901)
           AND STATUS IN ('WAITING', 'ACTIVE');
UPDATE T_DUEL
           SET GUEST_ID   = 901
              ,STATUS     = 'ACTIVE'
              ,START_DATE = '2026-10-13'::DATE
              ,END_DATE   = '2026-10-19'::DATE
              ,UPD_DAY    = NOW()
         WHERE 1=1
           AND CODE      = 'duelcode01'
           AND STATUS    = 'WAITING'
           AND GUEST_ID IS NULL
           AND HOST_ID  <> 901
           AND INS_DAY   > NOW() - INTERVAL '3 days';
UPDATE T_DUEL
           SET GUEST_ID   = 902
              ,STATUS     = 'ACTIVE'
              ,START_DATE = '2026-10-13'::DATE
              ,END_DATE   = '2026-10-19'::DATE
              ,UPD_DAY    = NOW()
         WHERE 1=1
           AND CODE      = 'duelcode01'
           AND STATUS    = 'WAITING'
           AND GUEST_ID IS NULL
           AND HOST_ID  <> 902
           AND INS_DAY   > NOW() - INTERVAL '3 days';
SELECT  D.ID                                                AS "id"
               ,D.CODE                                              AS "code"
               ,D.HOST_ID                                           AS "hostId"
               ,H.NICKNAME                                          AS "hostName"
               ,D.GUEST_ID                                          AS "guestId"
               ,G.NICKNAME                                          AS "guestName"
               ,D.STATUS                                            AS "status"
               ,TO_CHAR(D.START_DATE, 'YYYY-MM-DD')                 AS "startDate"
               ,TO_CHAR(D.END_DATE, 'YYYY-MM-DD')                   AS "endDate"
               ,D.HOST_SCORE                                        AS "hostScore"
               ,D.GUEST_SCORE                                       AS "guestScore"
               ,D.WINNER_ID                                         AS "winnerId"
               ,(EXTRACT(EPOCH FROM D.INS_DAY) * 1000)::BIGINT      AS "insAt"
          FROM T_DUEL D
          JOIN T_USER H ON H.ID = D.HOST_ID
          LEFT JOIN T_USER G ON G.ID = D.GUEST_ID
    
         WHERE 1=1
           AND D.CODE = 'duelcode01';
SELECT  D.ID                                                AS "id"
               ,D.CODE                                              AS "code"
               ,D.HOST_ID                                           AS "hostId"
               ,H.NICKNAME                                          AS "hostName"
               ,D.GUEST_ID                                          AS "guestId"
               ,G.NICKNAME                                          AS "guestName"
               ,D.STATUS                                            AS "status"
               ,TO_CHAR(D.START_DATE, 'YYYY-MM-DD')                 AS "startDate"
               ,TO_CHAR(D.END_DATE, 'YYYY-MM-DD')                   AS "endDate"
               ,D.HOST_SCORE                                        AS "hostScore"
               ,D.GUEST_SCORE                                       AS "guestScore"
               ,D.WINNER_ID                                         AS "winnerId"
               ,(EXTRACT(EPOCH FROM D.INS_DAY) * 1000)::BIGINT      AS "insAt"
          FROM T_DUEL D
          JOIN T_USER H ON H.ID = D.HOST_ID
          LEFT JOIN T_USER G ON G.ID = D.GUEST_ID
    
         WHERE 1=1
           AND (D.HOST_ID = 902 OR D.GUEST_ID = 902)
           AND NOT (D.STATUS = 'EXPIRED' AND D.GUEST_ID IS NULL AND D.INS_DAY < NOW() - INTERVAL '14 days')
         ORDER BY D.ID DESC
         LIMIT  20;
SELECT  D.ID                                                AS "id"
               ,D.CODE                                              AS "code"
               ,D.HOST_ID                                           AS "hostId"
               ,H.NICKNAME                                          AS "hostName"
               ,D.GUEST_ID                                          AS "guestId"
               ,G.NICKNAME                                          AS "guestName"
               ,D.STATUS                                            AS "status"
               ,TO_CHAR(D.START_DATE, 'YYYY-MM-DD')                 AS "startDate"
               ,TO_CHAR(D.END_DATE, 'YYYY-MM-DD')                   AS "endDate"
               ,D.HOST_SCORE                                        AS "hostScore"
               ,D.GUEST_SCORE                                       AS "guestScore"
               ,D.WINNER_ID                                         AS "winnerId"
               ,(EXTRACT(EPOCH FROM D.INS_DAY) * 1000)::BIGINT      AS "insAt"
          FROM T_DUEL D
          JOIN T_USER H ON H.ID = D.HOST_ID
          LEFT JOIN T_USER G ON G.ID = D.GUEST_ID
    
         WHERE 1=1
           AND D.STATUS   = 'ACTIVE'
           AND D.END_DATE < '2026-10-20'::DATE
         ORDER BY D.ID;
UPDATE T_DUEL
           SET STATUS      = 'DONE'
              ,HOST_SCORE  = 30
              ,GUEST_SCORE = 3
              ,WINNER_ID   = 901
              ,UPD_DAY     = NOW()
         WHERE 1=1
           AND ID     = (SELECT ID FROM T_DUEL WHERE CODE='duelcode01')
           AND STATUS = 'ACTIVE';
UPDATE T_DUEL
           SET STATUS  = 'EXPIRED'
              ,UPD_DAY = NOW()
         WHERE 1=1
           AND CODE    = 'duelcode01'
           AND HOST_ID = 901
           AND STATUS  = 'WAITING';
UPDATE T_DUEL
           SET STATUS  = 'EXPIRED'
              ,UPD_DAY = NOW()
         WHERE 1=1
           AND STATUS  = 'WAITING'
           AND INS_DAY < NOW() - INTERVAL '3 days';
SELECT NICKNAME FROM T_USER WHERE ID = 902;
