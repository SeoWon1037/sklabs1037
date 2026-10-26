package com.sklabs1037.sklabs1037.autofix.service;

import com.sklabs1037.sklabs1037.client.ApiCallLog;
import com.sklabs1037.sklabs1037.client.RestClients;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * GitHub REST API (이 저장소 하나만)
 *
 * - 토큰: 서버 .env 의 GITHUB_TOKEN (이 저장소만 · Issues/Pull requests/Contents 읽기·쓰기만 준 fine-grained 토큰)
 *   → 이 토큰으로 PR을 합치면 main push로 기존 배포(deploy.yml)가 그대로 돌아감
 *   (Actions 안의 기본 GITHUB_TOKEN으로 합치면 다음 워크플로가 안 돌기 때문에 서버가 합침)
 * - 저장소: GITHUB_REPO (기본 SeoWon1037/sklabs1037)
 */
@Component
public class GitHubClient {

    @Value("${github.token:}")
    private String token;

    @Value("${github.repo:SeoWon1037/sklabs1037}")
    private String repo;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public boolean configured() {

        return token != null && !token.isBlank();

    }

    public String repo() {

        return repo;

    }

    // 이슈 만들기 → 이슈 번호
    public int createIssue(String title, String body, List<String> labels) {

        Map<?, ?> res = call("POST", "/issues", Map.of("title", title, "body", body, "labels", labels));
        return ((Number) res.get("number")).intValue();

    }

    // PR 합치기 (squash: main에는 커밋 하나로)
    public void mergePull(int number, String title) {

        call("PUT", "/pulls/" + number + "/merge", Map.of("merge_method", "squash", "commit_title", title));

    }

    public void closePull(int number) {

        call("PATCH", "/pulls/" + number, Map.of("state", "closed"));

    }

    public void closeIssue(int number) {

        call("PATCH", "/issues/" + number, Map.of("state", "closed", "state_reason", "not_planned"));

    }

    public void comment(int issueNumber, String body) {

        call("POST", "/issues/" + issueNumber + "/comments", Map.of("body", body));

    }

    private Map<?, ?> call(String method, String path, Map<String, Object> body) {

        if (!configured()) throw new IllegalStateException("GITHUB_TOKEN이 설정되지 않았어요.");

        try {

            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
                    .build();

            // 🔌 연동 기록 (경로의 이슈·PR 번호는 숫자 → # 으로 묶어서 "issues/#/comments"처럼)
            String action = method + " " + path.replaceAll("/\\d+", "/#").replaceFirst("^/", "");
            long start = ApiCallLog.start();
            HttpResponse<String> res;

            try {
                res = http.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (java.io.IOException | InterruptedException e) {
                ApiCallLog.record("GITHUB", action, false, null, ApiCallLog.elapsedMs(start), RestClients.errorText(e));
                throw e;
            }

            boolean ok = res.statusCode() / 100 == 2;
            ApiCallLog.record("GITHUB", action, ok, res.statusCode(), ApiCallLog.elapsedMs(start), ok ? null : "HTTP " + res.statusCode());

            if (!ok) {
                String msg = res.body() == null ? "" : res.body();
                throw new IllegalStateException("GitHub " + res.statusCode() + ": " + (msg.length() > 200 ? msg.substring(0, 200) : msg));
            }
            String text = res.body();
            return text == null || text.isBlank() ? Map.of() : jsonMapper.readValue(text, Map.class);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub 요청이 중단됐어요.");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("GitHub에 연결하지 못했어요: " + e.getMessage());
        }

    }
}
