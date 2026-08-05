# 1단계: 빌드
FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

# 2단계: 실행
FROM eclipse-temurin:17-jre
WORKDIR /app
# heic/heif 업로드 이미지를 jpg로 변환하는 데 사용 (S3StorageService, heif-convert)
# libde265: heic 내부에 실제 인코딩된 HEVC 픽셀을 디코딩하는 코덱 플러그인 - 없으면 컨테이너 파싱만 되고
# "Decoder plugin generated an error: Unspecified"로 디코딩 자체가 실패한다.
RUN apt-get update && apt-get install -y --no-install-recommends libheif-examples libde265-0 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

