package com.sklabs1037.sklabs1037.user.service;

import com.sklabs1037.sklabs1037.user.dao.UserOAuthDao;
import com.sklabs1037.sklabs1037.user.vo.UserOAuthVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.sklabs1037.sklabs1037.client.RestClients;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * 소셜 로그인 화면 Service 구현
 * - 네이버/카카오와 대화(토큰 받기, 회원 정보 받기)하고, 우리 회원으로 연결하는 곳
 * - 회원 정보를 받은 뒤 처리(기존 회원 / 이메일 중복 / 자동 가입)는 둘이 똑같아서 findOrCreateUser()로 공통 처리
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class UserOAuthServiceImpl implements IUserOAuthService {

    // 네이버 주소들
    private static final String NAVER_AUTHORIZE_URL = "https://nid.naver.com/oauth2.0/authorize";   // 로그인 화면
    private static final String NAVER_TOKEN_URL     = "https://nid.naver.com/oauth2.0/token";       // 열쇠(토큰) 받기
    private static final String NAVER_PROFILE_URL   = "https://openapi.naver.com/v1/nid/me";        // 회원 정보 받기

    // 카카오 주소들
    private static final String KAKAO_AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";    // 로그인 화면
    private static final String KAKAO_TOKEN_URL     = "https://kauth.kakao.com/oauth/token";        // 열쇠(토큰) 받기
    private static final String KAKAO_PROFILE_URL   = "https://kapi.kakao.com/v2/user/me";          // 회원 정보 받기

    // 받은 JSON을 Map으로 받기 위한 타입 정보
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Autowired
    private UserOAuthDao userOAuthDao;

    // ===== application.properties 값 (환경변수에서 옴) =====
    @Value("${naver.client-id}")
    private String naverClientId;

    @Value("${naver.client-secret}")
    private String naverClientSecret;

    @Value("${naver.redirect-uri}")
    private String naverRedirectUri;

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @Value("${kakao.client-secret}")
    private String kakaoClientSecret;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;

    // 다른 서버(네이버/카카오)에 HTTP 요청을 보내는 도구 (브라우저의 fetch 같은 역할을 Java에서)
    // - 응답 10초 제한 + 🔌 연동 기록 (주소를 보고 KAKAO / NAVER로 나눠 기록)
    private final RestClient restClient = RestClients.withTimeout(RestClients.BY_HOST, 10);

    // ============================================
    // 네이버
    // ============================================

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

        String accessToken = requestNaverAccessToken(code, state);   // 1. code → 열쇠
        UserOAuthVO profile = requestNaverProfile(accessToken);      // 2. 열쇠 → 회원 정보

        return findOrCreateUser(profile);                            // 3. 우리 회원으로 연결

    }

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
                .header("Authorization", "Bearer " + accessToken)
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
        profile.setNickname(makeNickname((String) response.get("nickname"), "네이버회원"));

        return profile;

    }

    // ============================================
    // 카카오
    // ============================================

    // 카카오 로그인 화면 주소 만들기
    @Override
    public String getKakaoLoginUrl(String state) {

        return UriComponentsBuilder.fromUriString(KAKAO_AUTHORIZE_URL)
                .queryParam("response_type", "code")          // "로그인 끝나면 code를 줘"
                .queryParam("client_id", kakaoClientId)       // REST API 키
                .queryParam("redirect_uri", kakaoRedirectUri) // 끝나면 돌아올 주소
                .queryParam("state", state)                   // 위조 방지용 랜덤값
                .encode()
                .toUriString();

    }

    // 카카오 로그인 처리
    @Override
    public UserOAuthVO loginWithKakao(String code) throws Exception {

        String accessToken = requestKakaoAccessToken(code);          // 1. code → 열쇠
        UserOAuthVO profile = requestKakaoProfile(accessToken);      // 2. 열쇠 → 회원 정보

        return findOrCreateUser(profile);                            // 3. 우리 회원으로 연결

    }

    // code를 주고 열쇠(Access Token) 받기
    // ※ 카카오는 주소 뒤가 아니라 "폼(form) 형식 본문"으로 보내야 함
    private String requestKakaoAccessToken(String code) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", kakaoClientId);
        form.add("client_secret", kakaoClientSecret);
        form.add("redirect_uri", kakaoRedirectUri);   // 로그인 화면 주소 만들 때와 같은 값이어야 함
        form.add("code", code);

        Map<String, Object> result = restClient.post()
                .uri(KAKAO_TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP_TYPE);

        if (result == null || result.get("access_token") == null) {

            throw new IllegalStateException("카카오 토큰 발급 실패: " + result);

        }

        return (String) result.get("access_token");

    }

    // 열쇠로 회원 정보 받기
    // 카카오 응답 모양: { id: 123456789, kakao_account: { email, is_email_verified, profile: { nickname } } }
    @SuppressWarnings("unchecked")
    private UserOAuthVO requestKakaoProfile(String accessToken) {

        Map<String, Object> result = restClient.get()
                .uri(KAKAO_PROFILE_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(MAP_TYPE);

        if (result == null || result.get("id") == null) {

            throw new IllegalStateException("카카오 회원 정보 조회 실패: " + result);

        }

        Map<String, Object> account = (Map<String, Object>) result.get("kakao_account");
        Map<String, Object> kakaoProfile = (account == null) ? null : (Map<String, Object>) account.get("profile");

        String email    = (account == null) ? null : (String) account.get("email");
        Object verified = (account == null) ? null : account.get("is_email_verified");
        String nickname = (kakaoProfile == null) ? null : (String) kakaoProfile.get("nickname");

        // 이메일이 없거나, 카카오에서 인증 안 된 이메일이면 가입 막기
        if (email == null || !Boolean.TRUE.equals(verified)) {

            throw new IllegalArgumentException("email_missing");

        }

        UserOAuthVO profile = new UserOAuthVO();
        profile.setProvider("KAKAO");
        profile.setProviderId(String.valueOf(result.get("id")));   // 카카오 id는 숫자 → 문자로 바꿔서 저장
        profile.setEmail(email);
        profile.setNickname(makeNickname(nickname, "카카오회원"));

        return profile;

    }

    // ============================================
    // 공통
    // ============================================

    // 받은 회원 정보로 우리 회원 찾기 / 자동 가입
    private UserOAuthVO findOrCreateUser(UserOAuthVO profile) {

        // 1. 이미 같은 경로(NAVER/KAKAO)로 가입한 회원이면 그대로 로그인
        UserOAuthVO user = userOAuthDao.selectUserByProvider(profile);

        if (user != null) {

            return user;

        }

        // 2. 같은 이메일이 이미 있으면 막기 (이메일 가입 or 다른 소셜로 가입한 경우)
        if (userOAuthDao.countByEmail(profile) > 0) {

            throw new IllegalArgumentException("email_exists");

        }

        // 3. 처음이면 자동 가입
        profile.setRole("USER");
        userOAuthDao.insertOAuthUser(profile);   // useGeneratedKeys로 profile.id가 채워짐

        return profile;

    }

    // 닉네임 정리 (없으면 기본값, 50자 넘으면 자르기 - DB 컬럼 길이)
    private String makeNickname(String nickname, String defaultName) {

        if (nickname == null || nickname.trim().isEmpty()) {

            return defaultName;

        }

        String trimmed = nickname.trim();

        return trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed;

    }
}