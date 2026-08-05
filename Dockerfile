# 1단계: 빌드
FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

# 2단계: 실행
FROM eclipse-temurin:17-jre
WORKDIR /app
# heic/heif 업로드 이미지를 jpg로 변환하는 데 사용 (S3StorageService, heif-convert)
RUN apt-get update && apt-get install -y --no-install-recommends libheif-examples \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

