# ============================================
# 백엔드 이미지 (2단계 빌드)
# 1단계: JDK로 jar 파일 만들기
# 2단계: 실행에 필요한 JRE + jar만 담기 → 이미지 크기 작아짐
# ============================================

# ===== 1단계: 빌드 =====
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Gradle 설정 먼저 복사 → 소스만 바뀌면 의존성 다운로드 단계는 캐시 재사용
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# 윈도우에서 만든 파일의 줄바꿈(CRLF)을 리눅스용(LF)으로 + 실행 권한
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

# 소스 복사 후 jar 만들기 (테스트는 CI에서 이미 하니까 생략)
COPY src src
RUN ./gradlew bootJar -x test --no-daemon \
 && cp $(ls build/libs/*.jar | grep -v plain) app.jar

# ===== 2단계: 실행 =====
FROM eclipse-temurin:17-jre
WORKDIR /app

# 한국 시간 (스케줄러, 로그 시간 기준)
ENV TZ=Asia/Seoul

# 메모리 제한 (EC2 프리티어는 메모리가 1GB라서)
ENV JAVA_OPTS="-Xms256m -Xmx512m"

COPY --from=build /app/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]