# 보안 점검 결과 (2026-08-05)

대상: `main`/`cors-fix` 기준 코드 전체. 운영 프로필은 `docker` (docker-compose.yml → `SPRING_PROFILES_ACTIVE: docker`).

| # | 심각도 | 항목 | 위치 | 상태 |
|---|---|---|---|---|
| 1 | **Critical** | 아티스트 CRUD가 인증 없이 열려 있음 | `Config/SecurityConfig.java` | ✅ 조치완료 |
| 2 | **High** | 콘서트 CRUD·KOPIS 동기화가 일반 회원 누구나 가능 | `Concert/controller/ConcertController.java` | ✅ 조치완료 |
| 3 | **Medium** | Refresh Token을 Access Token으로 그대로 사용 가능 | `common/jwt/JwtTokenProvider.java` | ✅ 조치완료 |
| 4 | **Medium** | WebSocket 인증이 블랙리스트/정지 상태를 확인하지 않음 | `Chat/interceptor/StompSubscriptionInterceptor.java` | ✅ 조치완료 |
| 5 | **Medium** | 이메일 인증코드 입력 시도 횟수 제한 없음 | `User/service/EmailVerificationServiceImpl.java` | 미조치 |
| 6 | Low | 운영 환경에 `/dev/admin.html` 정적 파일이 공개됨 | `Config/SecurityConfig.java:76` | 미조치 |
| 7 | Low | `application-local.yaml`에 실제 자격증명 평문 보관 | `src/main/resources/` | 미조치 |

작업 브랜치: `security-review`

---

## 1. (Critical) 아티스트 등록/수정/삭제가 인증 없이 호출 가능

`SecurityConfig`의 permitAll 목록에 `/api/artists/**`가 통째로 들어가 있다.

```java
// Config/SecurityConfig.java:149
"/ws/chat/**",
"/api/artists/**"
).permitAll()
```

그런데 `ArtistController`에는 조회뿐 아니라 변경 API도 같은 경로에 있고, 컨트롤러/서비스 어디에도 별도 권한 체크가 없다.

- `POST   /api/artists` — 아티스트 등록
- `PUT    /api/artists/{id}` — 수정
- `DELETE /api/artists/{id}` — 삭제

즉 **토큰 없이** 아무나 아티스트 데이터를 지우거나 조작할 수 있다. 아티스트는 콘서트·관심아티스트·KOPIS 동기화의 기준 데이터라 삭제 시 연쇄 피해가 크다.

**조치완료**: permitAll 목록에서 `/api/artists/**`를 빼고, 조회만 열고 변경은 ADMIN으로 제한.

```java
// permitAll 목록에서 "/api/artists/**" 제거 후
.requestMatchers(HttpMethod.GET, "/api/artists/**").permitAll()
.requestMatchers("/api/artists/**").hasRole("ADMIN")
```

관리자용 아티스트 CRUD는 이미 `/api/admin/artists`에 있으므로, 후속으로 `ArtistController`의 변경 메서드를 아예 삭제하는 편이 더 깔끔하다.

## 2. (High) 콘서트 CRUD가 로그인만 하면 누구나 가능

`/api/concerts/**`는 `anyRequest().authenticated()`에 걸리므로 인증은 요구하지만 **역할 체크가 없다**. `ConcertController`의 아래 메서드들은 `@AuthenticationPrincipal`조차 받지 않는다.

- `POST /api/concerts` (`ConcertController.java:40`)
- `PUT /api/concerts/{id}` (`:57`)
- `DELETE /api/concerts/{id}` (`:75`)
- `POST /api/concerts/sync` — KOPIS 전체 동기화 수동 트리거 (`:148`)

테스트 계정 하나만 만들면 전체 공연 데이터를 지우거나, `/sync`를 반복 호출해 외부 KOPIS API 쿼터를 소진시킬 수 있다.

**조치완료**: 쓰기 메서드만 ADMIN으로 제한. 조회(GET)는 기존대로 로그인 사용자에게 열려 있다.

```java
.requestMatchers(HttpMethod.POST,   "/api/concerts/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.PUT,    "/api/concerts/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/concerts/**").hasRole("ADMIN")
```

관리자용 동일 기능이 `/api/admin/concerts`에 이미 있으므로, 후속으로 `ConcertController`의 쓰기 메서드 4개를 제거하는 편이 더 깔끔하다.

## 3. (Medium) Access/Refresh Token 구분이 없음

### 문제

변경 전 `createToken()`이 만드는 payload는 두 토큰이 동일했다.

```json
{ "sub": "42", "iat": 1754000000, "exp": 1754001800 }
```

Access든 Refresh든 **`exp` 값만 다르고 나머지는 완전히 같다.** 그리고 `validateToken()`은 "서명이 맞고 안 만료됐나"만 검사했다.

여기서 오용은 두 방향이 가능한데, 실제로 뚫리는 건 한쪽이었다.

**뚫리던 쪽 — Refresh를 Access로 사용.**
`Authorization: Bearer {refreshToken}`으로 요청하면 `validateToken()`이 통과시키고, `getUserId()`가 `sub`에서 userId를 꺼내 그대로 인증된다. **Access Token에 30분 만료를 걸어둔 의미가 사라지고 사실상 14일짜리 세션이 된다.**

**막혀 있던 쪽 — Access를 Refresh로 사용.**
`/api/auth/refresh`에 Access Token을 넣어도 `RefreshTokenService.isValid()`가 Redis 저장값과 대조해서 걸린다. 즉 이쪽은 JWT 검증이 아니라 **Redis 대조가 우연히 막아주고 있던** 상태였다.

### 조치

**① 발급 시 `typ` claim 추가**

```java
private static final String TYPE_CLAIM = "typ";
private static final String TYPE_ACCESS = "access";
private static final String TYPE_REFRESH = "refresh";

private String createToken(Long userId, String type, long expiration) {
    return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim(TYPE_CLAIM, type)   // ← 추가
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact();
}
```

**② 검증을 용도별로 분리**

```java
public boolean validateAccessToken(String token)  { return validate(token, TYPE_ACCESS); }
public boolean validateRefreshToken(String token) { return validate(token, TYPE_REFRESH); }

private boolean validate(String token, String expectedType) {
    try {
        return expectedType.equals(parseClaims(token).get(TYPE_CLAIM, String.class));
    } catch (ExpiredJwtException e) {
        return false;
    } catch (JwtException | IllegalArgumentException e) {
        return false;
    }
}
```

기존 `validateToken()`은 **삭제했다.** 남겨두면 "아무 토큰이나 통과시키는" 문을 누군가 다시 쓰게 된다. 호출부는 세 곳뿐이라 전부 교체됐다 — REST 필터·STOMP 인터셉터는 `validateAccessToken`, `AuthService.reissue`는 `validateRefreshToken`.

### 설계 판단 근거

**왜 키 분리가 아니라 claim인가.**
Access/Refresh에 서로 다른 시크릿을 쓰는 방법이 암호학적으로는 더 강하다. 다만 시크릿이 하나 더 늘어 `.env`·`docker-compose.yml`·`application*.yml`을 전부 건드려야 하고, 로테이션 대상도 둘이 된다. **얻는 것 대비 운영 부담이 커서** claim 한 줄로 갔다. 서명 위조는 이미 막혀 있고, 여기서 필요한 건 "위조" 차단이 아니라 "용도 혼동" 차단이다.

**왜 claim 이름이 `typ`인가.**
JWT 헤더의 `typ`와 이름이 겹치지만 payload claim이라 충돌하지 않고, 관례상 가장 흔한 이름이다.

**왜 claim이 없으면 false인가 (= 구버전 토큰 무효).**
`get(TYPE_CLAIM, String.class)`가 null을 반환해 `equals` 비교에서 false가 된다. **의도한 동작이다.** 여기서 "claim 없으면 access로 간주"하는 유예를 두면 기존 Refresh Token들이 14일간 계속 Access로 통용된다 — 고치려던 문제가 그 기간만큼 그대로 남는다.

무중단을 원하면 유예를 뒀다가 2주 뒤 제거하는 2단계 배포가 가능하지만, 그동안 취약점이 살아있고 2단계를 잊기 쉬워서 택하지 않았다.

> ⚠️ **대가 — 배포 즉시 전원 재로그인.** `typ` 없는 토큰이 전부 무효라 살아있던 세션이 모두 끊긴다. 특히 **`/api/auth/refresh`도 401을 반환한다** (쿠키의 Refresh Token도 구버전이므로). 프론트가 `401 → refresh → 401`에서 무한루프 없이 로그인 화면으로 빠지는지 확인 필요. 한산한 시간대에 배포할 것.

### 검증

`JwtTokenProviderTest`에 교차 사용 차단을 명시했다.

```java
@Test
void refresh_토큰은_access_토큰으로_사용할_수_없다() {
    assertThat(jwtTokenProvider.validateAccessToken(
            jwtTokenProvider.createRefreshToken(1L))).isFalse();
}

@Test
void access_토큰은_재발급용으로_사용할_수_없다() {
    assertThat(jwtTokenProvider.validateRefreshToken(
            jwtTokenProvider.createAccessToken(1L))).isFalse();
}
```

`TokenAuthenticatorTest`의 `Refresh_Token으로는_인증되지_않는다`가 provider 단위뿐 아니라 **실제 인증 경로에서도** 막히는지 확인한다.

### 범위의 한계

이 변경이 줄이는 것은 "토큰을 이미 손에 넣은 뒤의 오용 범위"다. Refresh Token 자체는 HttpOnly 쿠키(`CookieUtil`)라 JS가 읽을 수 없어 원래도 XSS 탈취는 어려웠다. 다만 로그·프록시·에러 리포팅 등으로 토큰이 새는 경로는 여럿이고, 그때 피해가 30분이냐 14일이냐의 차이다.

## 4. (Medium) WebSocket CONNECT 인증이 얕음

```java
// Chat/interceptor/StompSubscriptionInterceptor.java:49
private void handleConnect(StompHeaderAccessor accessor) {
    ...
    if (!jwtTokenProvider.validateToken(token)) { ... }
    Long userId = jwtTokenProvider.getUserId(token);
    accessor.setUser(() -> userId.toString());
}
```

REST 쪽 `JwtAuthenticationFilter`가 하는 두 가지 검사가 여기엔 없다.

- `tokenBlacklistService.isBlacklisted(token)` — **로그아웃한 토큰으로도 채팅 접속이 된다.**
- `Status.SUSPENDED` 확인 — **이용정지된 계정이 채팅은 계속 쓸 수 있다.**

3번과 겹쳐서, Refresh Token으로 WS에 붙는 것도 가능하다.

**조치완료**: 검증 규칙을 `common/jwt/TokenAuthenticator`로 모으고 REST 필터·STOMP 인터셉터가 함께 쓰도록 했다. 이제 양쪽 모두 **토큰 타입 → 블랙리스트 → 계정 상태**를 동일하게 검사한다.

같이 닫힌 구멍:
- **탈퇴 계정** — `withdrawUser()`는 Refresh Token만 지워서 남은 Access Token이 최대 30분 살아 있었다. 이제 `WITHDRAWN`이면 410으로 차단된다 (REST/WS 공통).
- **삭제된 사용자** — 기존 필터는 사용자 row가 없어도 `Role.USER`로 인증을 통과시켰다. 이제 미존재 계정은 인증 실패.

### WS는 왜 Access Token만 받는가

3번의 타입 분리에 따라 STOMP CONNECT는 Access Token만 통과시킨다. 이유는 셋이다.

1. **애초에 Refresh Token은 JS가 읽을 수 없다.** `CookieUtil`이 `httpOnly(true)`로 내려주므로 프론트가 값을 꺼낼 수 없고, STOMP CONNECT의 `Authorization` 헤더는 JS가 직접 채우는 것이다. Refresh Token을 넣으려면 HttpOnly를 포기해야 하고, 그러면 XSS 한 번에 14일짜리 자격증명이 털린다.
2. **유출 시 피해 범위가 30분 vs 14일이다.** Refresh Token의 역할을 "새 토큰 발급" 하나로 좁혀두는 것이 요점이고, 그래서 Redis 저장값 대조라는 별도 관문도 붙어 있다.
3. **로그아웃 무효화가 Access Token 기준으로 돈다.** `logout()`은 넘겨받은 Access Token을 블랙리스트에 넣는다. WS가 Refresh Token을 받으면 블랙리스트가 겨냥하는 대상이 어긋나 로그아웃해도 채팅이 안 끊긴다.

프론트 입장에서는 사실상 변화가 없다 — 1번 때문에 원래도 Access Token을 쓸 수밖에 없었다.

**남은 한계**: CONNECT 시점에만 검증한다. Access Token은 30분인데 WS 연결은 몇 시간씩 유지되므로, **한 번 붙고 나면 토큰이 만료돼도 연결은 계속 살아 있다.** 덕분에 프론트는 토큰 갱신 때마다 재연결할 필요가 없지만, 반대로 정지/탈퇴 처리를 해도 이미 붙은 세션은 즉시 끊기지 않는다. 제재 적용 시 해당 유저 WS 세션을 강제 종료하거나 주기적으로 재검증하는 로직은 별도 과제.

## 5. (Medium) 이메일 인증코드 brute-force 가능

`EmailVerificationServiceImpl`은 **발송** 쪽만 제한한다 (10초 쿨다운, 시간당 5회, 일 10회). 반면 `verifyCode()`에는 시도 횟수 카운터가 없다.

코드는 6자리 숫자(1,000,000 경우의 수)이고 TTL 3분이다. 동시 요청으로 초당 수백 건을 던지면 무시할 수 없는 확률로 맞출 수 있고, 실패해도 코드가 무효화되지 않아 TTL 내내 계속 시도할 수 있다.

**수정**: `LoginAttemptService`와 같은 패턴으로 `email_verify_fail:{userId}` 카운터를 두고 5회 실패 시 코드를 삭제한다.

## 6. (Low) 운영 환경에 `/dev/admin.html` 공개

```java
// Config/SecurityConfig.java:76  @Profile({"dev","docker"})
.requestMatchers("/dev/chat-test.html").denyAll()
.anyRequest().permitAll();
```

`chat-test.html`만 막고 `/dev/admin.html`은 인증 없이 열린다. API 자체는 `hasRole("ADMIN")`으로 잠겨 있어 데이터 유출은 없지만, 관리자 API 전체 목록·파라미터 구조가 그대로 노출된다.

**수정**: `docker` 프로필에서는 `/dev/**` 전체를 `denyAll()` 하거나, 스웨거처럼 Basic Auth 뒤에 둔다.

## 7. (Low) 로컬 설정 파일의 평문 자격증명

`src/main/resources/application-local.yaml`에 실제 값이 들어 있다.

- Gmail 앱 비밀번호 `rjrrzolfynbrwdlm` (loopconcert@gmail.com)
- KOPIS API 키 `aeac5fad99524488ad66fe029dfd6469`
- JWT 시크릿

`.gitignore`에 등록되어 있고 `git log -S`로 확인한 결과 **커밋 이력에 올라간 적은 없다**. 다만 이 파일이 팀 채널 등으로 공유된 적이 있다면 위 두 자격증명은 실제 계정 것이므로 로테이션이 필요하다. 로컬 전용 값은 더미로 교체하는 편이 안전하다.

---

## 문제없다고 판단한 부분

확인했고 조치가 필요 없는 항목들:

- **SQL Injection** — 전 구간 Spring Data JPA + 명명 파라미터. 네이티브 쿼리나 문자열 연결 쿼리 없음.
- **비밀번호 저장** — BCrypt. 로그인 실패 횟수 제한(`LoginAttemptService`)도 있음.
- **CSRF** — 비활성이지만 API 인증이 `Authorization` 헤더 기반이라 해당 없음. 쿠키를 쓰는 `/api/auth/refresh`는 응답 바디를 공격자가 CORS 때문에 읽을 수 없다.
- **채팅 XSS** — `HtmlSanitizer`(jsoup `Safelist.none()`)로 저장 전 정화 (`ChatServiceImpl.java:402`).
- **파일 업로드** — 확장자·Content-Type 화이트리스트(jpg/png/webp), 5MB 제한, S3 키는 UUID 생성이라 경로 조작 불가. `heif-convert`는 `ProcessBuilder` 인자 배열로 실행해 셸 인젝션 없음.
- **채팅방 접근 제어** — `/sub/chat/room/{id}` 구독 시 참여자 여부, `/sub/chat/errors/{id}` 구독 시 본인 여부를 검증.
- **Kakao OAuth** — `redirect_uri` 화이트리스트 검증 있음.
- **테스트용 API** — `TestUserController`(임의 ADMIN 계정 생성)는 `@Profile("!docker")`, `AdminSeeder`(admin/0000)는 `@Profile("local")`이라 운영에 등록되지 않음.
- **Swagger** — `local` 외 프로필에서 전용 Basic Auth로 게이트.
- **에러 응답** — `GlobalExceptionHandler`가 정해진 `ErrorCode`만 반환. 스택트레이스·내부 메시지 노출 없음.
- **개인정보 감사 로그** — 관리자 조회 시 `AdminAccessLog` 기록.

## 전달사항 — 서버 운영

**`.env`에 `CORS_ALLOWED_ORIGINS` 추가 (필수).**

```
CORS_ALLOWED_ORIGINS=https://프론트도메인
```

`docker-compose.yml`에 전달 라인은 추가했지만 폴백이 없다. `.env`에 이 키가 없으면 **빈 문자열이 "설정된 상태"로 주입**되어 `application.yaml`의 `${CORS_ALLOWED_ORIGINS:${FRONTEND_URL}}` 폴백이 걸리지 않고, 허용 origin이 빈 배열이 되어 CORS가 전부 막힌다. 값은 콤마 구분이며 `setAllowedOriginPatterns`라 `https://*.loop.io.kr` 같은 패턴도 된다.

`CHAT_ALLOWED_ORIGINS`도 같은 함정이다. 기존에 있던 키지만 값이 실제로 채워져 있는지 확인할 것.

신규 추가는 이 하나뿐이며 삭제·이름변경은 없다. (`@Value` 플레이스홀더와 `application*.yml`의 `${ENV}`를 전부 추출해 compose 주입 목록과 대조 확인. 남는 `KAKAO_REDIRECT_URI`는 `KAKAO_ALLOWED_REDIRECT_URIS`의 폴백일 뿐이고 그 값은 주입되므로 무해.)

## 전달사항 — 프론트엔드

**1. 배포 직후 전원 로그아웃.** `/api/auth/refresh`도 401을 반환하므로 `401 → refresh → 401`에서 무한루프 없이 로그인 화면으로 빠지는지 확인 필요. (→ #3)

**2. 정지 계정 응답의 필드명 변경.** 이 케이스만 필터에서 수동 JSON을 찍고 있어 나머지 API와 모양이 달랐다. 이번에 `CommonResponse.fail`로 통일했다.

```diff
- {"success":false,"message":"이용정지된 계정입니다.","data":null,"status":403}
+ {"success":false,"message":"이용정지된 계정입니다.","code":403}
```

앱의 다른 모든 에러 응답은 원래부터 `code`였다 (`GlobalExceptionHandler.respond`, `SecurityConfig.writeErrorResponse` 모두 `CommonResponse.fail` 경유). 공통 에러 핸들러에서 `code`를 읽고 있었다면 지금까지 정지 계정만 `undefined`로 잡히던 것이 정상화된 것이고, 이 케이스만 `status`로 분기해둔 코드가 있다면 수정이 필요하다. `data` 필드가 사라진 것은 `@JsonInclude(NON_NULL)` 때문이며 다른 에러 응답도 원래 `data`를 내려주지 않았다.

**3. 탈퇴 계정에 `410 WITHDRAWN_USER`가 새로 나간다.** 기존엔 탈퇴 후에도 남은 Access Token이 최대 30분 동작했다. 410 핸들링이 없으면 추가 필요. (→ #4)

**4. WS는 Access Token으로 연결.** 원래도 그럴 수밖에 없었으므로 변화 없음. 다만 로그아웃한 토큰은 블랙리스트로 막히므로 재로그인 후에는 새 토큰으로 다시 연결해야 한다. (→ #4)

**5. 아래를 호출하는 코드가 있는지 확인 필요.** 있으면 401/403이 난다.

- `POST`/`PUT`/`DELETE` `/api/artists`, `/api/artists/{id}`
- `POST`/`PUT`/`DELETE` `/api/concerts`, `/api/concerts/{id}`, `POST /api/concerts/sync`

전부 `/api/admin/artists`, `/api/admin/concerts`에 동일 기능이 있다. 어드민 콘솔(`static/dev/admin.html`)은 원래 `/api/admin/*`만 호출하므로 영향 없다. 조회(GET)는 전부 그대로다.

## 배포 체크리스트

- [ ] **EC2 `.env`에 `CORS_ALLOWED_ORIGINS` 추가.** `docker-compose.yml`에 전달 라인은 넣었지만 폴백이 없어서, `.env`에 값이 없으면 빈 문자열이 주입되고 허용 origin이 통째로 비어 CORS가 전부 막힌다. (`CHAT_ALLOWED_ORIGINS`도 동일)
- [ ] **전 사용자 재로그인 발생.** `typ` claim이 없는 기존 토큰은 모두 무효 → 한산한 시간대에 배포.
- [ ] 배포 후 확인: 비로그인 `POST /api/artists` → 401, 일반 회원 `DELETE /api/concerts/{id}` → 403, 어드민 콘솔 아티스트/공연 등록 정상.

## 권장 조치 순서

1. ~~`/api/artists/**` permitAll 제거~~ (#1) ✅
2. ~~콘서트 변경 API를 ADMIN으로 제한~~ (#2) ✅
3. ~~JWT 타입 claim + WS 인터셉터 블랙리스트/정지 검사~~ (#3, #4) ✅
4. 인증코드 실패 카운터 (#5)
5. `/dev/**` 운영 차단, 로컬 설정 더미화 (#6, #7)
