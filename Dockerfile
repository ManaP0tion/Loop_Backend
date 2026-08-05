# 1단계: 빌드
FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle build -x test --no-daemon

# 2단계: 실행
FROM eclipse-temurin:17-jre
WORKDIR /app
# heic/heif 업로드 이미지를 jpg로 변환하는 데 사용 (S3StorageService, heif-convert)
# libheif 1.13+는 코덱마다 별도 플러그인 패키지로 분리돼 있음 - libde265-0(raw 라이브러리)만으론 부족하고
# libheif가 실제로 로드하는 libheif-plugin-libde265가 있어야 HEVC(heic) 디코딩이 동작한다.
RUN apt-get update && apt-get install -y --no-install-recommends libheif-examples libheif-plugin-libde265 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

