package com.sklabs1037.sklabs1037.service;

import com.sklabs1037.sklabs1037.dao.UserOAuthDao;
import com.sklabs1037.sklabs1037.vo.UserOAuthVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * 소셜 로그인 화면 Service 구현
 * - 네이버와 대화(토큰 받기, 회원 정보 받기)하고, 우리 회원으로 연결하는 곳
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class UserOAuthServiceImpl implements IUserOAuthService {

    // 네이버 주소들
    private static final String NAVER_AUTHORIZE_URL = "https://nid.naver.com/oauth2.0/authorize";   // 로그인 화면
    private static final String NAVER_TOKEN_URL     = "https://nid.naver.com/oauth2.0/token";       // 열쇠(토큰) 받기
    private static final String NAVER_PROFILE_URL   = "https://openapi.naver.com/v1/nid/me";        // 회원 정보 받기

    // 네이버가 보내준 JSON을 Map으로 받기 위한 타입 정보
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Autowired
    private UserOAuthDao userOAuthDao;

    // application.properties 값 (환경변수에서 옴)
    @Value("${naver.client-id}")
    private String naverClientId;

    @Value("${naver.client-secret}")
    private String naverClientSecret;

    @Value("${naver.redirect-uri}")
    private String naverRedirectUri;

    // 다른 서버(네이버)에 HTTP 요청을 보내는 도구 (브라우저의 fetch 같은 역할을 Java에서)
    private final RestClient restClient = RestClient.create();

    // 네이버 로그인 화면 주소 만들기
    @Override
    public String getNaverLoginUrl(String state) {

        return UriComponentsBuilder.fromUriString(NAVER_AUTHORIZE_URL)
                .queryParam("response_type", "code")          // "로그인 끝나면 code를 줘"
                .queryParam("client_id", naverClientId)       // 우리 앱 번호
                .queryParam("redirect_uri", naverRedirectUri) // 끝나면 돌아올 주소
                .queryParam("state", state)                   // 위조 방지용 랜덤값
                .encode()
                .toUriString();

    }

    // 네이버 로그인 처리
    @Override
    public UserOAuthVO loginWithNaver(String code, String state) throws Exception {

        // 1. code → 열쇠(Access Token)
        String accessToken = requestNaverAccessToken(code, state);

        // 2. 열쇠 → 회원 정보
        UserOAuthVO profile = requestNaverProfile(accessToken);

        // 3. 이미 네이버로 가입한 회원이면 그대로 로그인
        UserOAuthVO user = userOAuthDao.selectUserByProvider(profile);

        if (user != null) {

            return user;

        }

        // 4. 같은 이메일로 이미 가입돼 있으면 막기 (한 이메일 = 한 계정)
        if (userOAuthDao.countByEmail(profile) > 0) {

            throw new IllegalArgumentException("email_exists");

        }

        // 5. 처음이면 자동 가입
        profile.setRole("USER");
        userOAuthDao.insertOAuthUser(profile);   // useGeneratedKeys로 profile.id가 채워짐

        return profile;

    }

    // ============================================
    // 네이버와 대화하는 부분
    // ============================================

    // code를 주고 열쇠(Access Token) 받기
    private String requestNaverAccessToken(String code, String state) {

        URI tokenUri = UriComponentsBuilder.fromUriString(NAVER_TOKEN_URL)
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", naverClientId)
                .queryParam("client_secret", naverClientSecret)
                .queryParam("code", code)
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();

        Map<String, Object> result = restClient.post()
                .uri(tokenUri)
                .retrieve()
                .body(MAP_TYPE);

        if (result == null || result.get("access_token") == null) {

            throw new IllegalStateException("네이버 토큰 발급 실패: " + result);

        }

        return (String) result.get("access_token");

    }

    // 열쇠로 회원 정보 받기
    @SuppressWarnings("unchecked")
    private UserOAuthVO requestNaverProfile(String accessToken) {

        Map<String, Object> result = restClient.get()
                .uri(NAVER_PROFILE_URL)
                .header("Authorization", "Bearer " + accessToken)   // "이 열쇠로 정보 줘"
                .retrieve()
                .body(MAP_TYPE);

        // 네이버는 성공하면 resultcode가 "00"
        if (result == null || !"00".equals(result.get("resultcode"))) {

            throw new IllegalStateException("네이버 회원 정보 조회 실패: " + result);

        }

        // 실제 정보는 "response" 안에 들어 있음 → { id, email, nickname }
        Map<String, Object> response = (Map<String, Object>) result.get("response");

        UserOAuthVO profile = new UserOAuthVO();
        profile.setProvider("NAVER");
        profile.setProviderId((String) response.get("id"));
        profile.setEmail((String) response.get("email"));
        profile.setNickname(makeNickname((String) response.get("nickname")));

        return profile;

    }

    // 닉네임 정리 (없으면 기본값, 50자 넘으면 자르기 - DB 컬럼 길이)
    private String makeNickname(String nickname) {

        if (nickname == null || nickname.trim().isEmpty()) {

            return "네이버회원";

        }

        String trimmed = nickname.trim();

        return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;

    }
}