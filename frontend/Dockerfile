# ============================================
# 프론트 이미지 (2단계 빌드)
# 1단계: Node로 React 빌드 → dist 폴더 (HTML/JS/CSS)
# 2단계: nginx가 dist를 보여주고, /api는 백엔드로 전달
# ============================================

# ===== 1단계: 빌드 =====
FROM node:22-alpine AS build
WORKDIR /app

# package.json 먼저 → 소스만 바뀌면 npm 설치 단계는 캐시 재사용
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ===== 2단계: 실행 =====
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80 443