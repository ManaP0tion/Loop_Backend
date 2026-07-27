# 관리자 페이지 백엔드 구현 계획

**브랜치:** `Admin` (dev `4a1598d` fast-forward 완료 · 2026-07-20)
**근거:** 이용약관 · 개인정보처리방침 · 「개인정보의 안전성 확보조치 기준」 제8조

---

## 0. 현재 상태 스냅샷

### 이미 존재 (재사용)
- JWT 기반 인증 (`common/jwt/JwtTokenProvider`, `JwtAuthenticationFilter`)
- `SecurityConfig` 2단계 필터체인 (`local/dev/docker` 전용 devtools + 프로덕션)
- `AsyncConfig` (접속기록 비동기 저장에 사용 예정)
- `common/exception/*` — 예외/응답 표준화
- `Notification` 도메인 (신고 채팅 열람 고지에 재사용)
- 스케줄러 패턴 (`Chat/scheduler/UnreadChatMailScheduler`)

### 이미 dev에서 완료 (스펙 요구 충족)
- 아티스트 삭제 → 콘서트 CASCADE 삭제 (`Concert.artist @OnDelete(CASCADE)`)
- 콘서트 삭제 → 동행글 CASCADE 삭제 (`CompanionPost.concert @OnDelete(CASCADE)`)
- 동행글 삭제 → 채팅방 `post_id` SET_NULL (채팅방 유지)

### 🚨 확인된 보안 이슈 (P0)
| 위치 | 문제 |
|---|---|
| `UserController.java:55` `GET /api/users` | 로그인만 하면 전체 회원 목록 조회 가능 |
| `UserController.java:62` `GET /api/users/{id}` | 로그인만 하면 남의 개인정보 조회 가능 |
| `UserController.java:138` `PUT /api/users/{id}/artists` | 남의 관심 아티스트 수정 가능 |
| `UserController.java:170` `DELETE /api/users/{id}` | 남 계정 탈퇴 처리 가능 |

### 개념적 공백
- **ROLE 개념 없음.** `User`에 role 컬럼 없음. JWT subject에 userId만 담김. `hasRole("ADMIN")` 사용 불가 상태.
- **접속기록 (Admin Access Log) 없음.** 처리방침 제10조 5항 미이행.
- **Report/Inquiry 상태 없음.** 처리단계(접수→처리중→조치완료/답변완료) 관리 불가.
- **`User.Status`에 `SUSPENDED` 없음.** 30일 정지 개념 미구현.

---

## 1. 설계 원칙

- **엔티티 확장 우선, 신규 테이블 최소화.** ROLE은 `User.role` 컬럼 하나. 관리자 계정 별도 테이블 없음.
- **접속기록은 AOP 어노테이션(`@LogAccess`)으로.** 컨트롤러마다 로그 코드 반복하지 않음. 스펙 표의 "남김" 항목에만 붙임.
- **관리자 엔드포인트 prefix `/api/admin/**` 일괄 잠금.** `SecurityConfig`에서 한 줄로 처리.
- **기존 서비스 재사용.** 관리자 서비스는 대개 얇은 래퍼(권한 체크 + 접속기록 어노테이션).
- **자동 해제/정리는 `@Scheduled`.** 이미 `UnreadChatMailScheduler` 패턴 있음.

---

## 2. 태스크 (우선순위 순)

### 🔴 P0 — 법·약관 직결 (베타 필수)

#### T1. 회원 조회 API 관리자 잠금
- `GET /api/users`, `GET /api/users/{id}` → `hasRole("ADMIN")`
- `PUT /api/users/{id}/artists`, `DELETE /api/users/{id}` → path `{id}` == `@AuthenticationPrincipal userId` 검증 (또는 경로를 `/me/artists`로 이동)
- **의존:** T2(ROLE 도입) 선행 필요

#### T2. ROLE 인증 체계
- `User/domain/Role.java` (enum `USER, ADMIN`)
- `User.role` 컬럼 추가 (기본값 `USER`)
- `JwtTokenProvider.createAccessToken(Long userId, Role role)` — 클레임에 `role` 추가
- `JwtAuthenticationFilter`에서 `SimpleGrantedAuthority("ROLE_" + role)` 부여
- `SecurityConfig`에 `.requestMatchers("/api/admin/**").hasRole("ADMIN")` 한 줄

#### T3. 관리자 접속기록 (Admin Access Log)
- **엔티티** `Admin/domain/AdminAccessLog`
  ```
  id, adminId, ip, targetUserId(nullable), action, path, createdAt
  ```
  index: `createdAt`, `adminId`, `targetUserId`
- **어노테이션** `@LogAccess(String action)` — 대상 메서드에 붙임
- **AOP** `AdminAccessLogAspect`
  - `@AfterReturning` — 예외 시 로그 안 남음(정상 조회만 기록). 필요 시 `@Around`로 확장.
  - `adminId` = SecurityContext, `ip` = HttpServletRequest, `targetUserId` = `@PathVariable Long id` 파라미터에서 리플렉션 추출
  - 저장은 `@Async` (AsyncConfig 이미 존재)
- **조회 API** `GET /api/admin/access-logs?from&to&adminId&targetUserId&action` — 월 1회 점검 화면
- **1년 보관** `@Scheduled(cron = "0 0 4 * * *")` 새벽 4시 `createdAt < now - 1y` 삭제

#### T4. 신고 처리 상태 관리
- `Report`에 필드 추가:
  ```
  status: ReportStatus (RECEIVED, IN_PROGRESS, RESOLVED, CLOSED)
  adminNote: String (처리 결과·사유)
  resolvedAt: LocalDateTime
  appealStatus: AppealStatus (NONE, MAINTAINED, RELEASED)
  ```
- `GET /api/admin/reports?status=&from=&to=` — 목록 (신고자 정보 마스킹은 관리자에겐 노출, 피신고자에겐 비공개는 UI 책임)
- `PATCH /api/admin/reports/{id}/status` — 상태·adminNote 변경
- `PATCH /api/admin/reports/{id}/appeal` — 이의제기 결과 (MAINTAINED/RELEASED)

#### T5. 신고 관련 채팅 열람
- `GET /api/admin/reports/{id}/chat` — 신고자·피신고자 간 DIRECT 채팅방의 메시지 조회
  - 신고 시점 이전/이후 모두 열람
  - 다른 상대와의 채팅방은 조회 불가 (DB 레벨에서 참여자 쌍 확인)
- 열람 시:
  - `AdminAccessLog` 남김 (`action="REPORT_CHAT_VIEW"`)
  - 양쪽 회원에게 `Notification` 발송 (약관 근거) — 기존 `Notification` 도메인 재사용

---

### 🟡 P1 — 회원/문의 관리

#### T6. 회원 정보 수정 (성별·생년월일)
- `PATCH /api/admin/users/{id}` — 성별, 생년월일만 수정 가능
- 근거: 약관 제6조 3항 (고객센터 통한 수정 요청 시)
- `@LogAccess("USER_MODIFY")` 필수

#### T7. 계정 정지·해지
- `User.Status`에 `SUSPENDED` 추가
- `User.suspendedUntil: LocalDateTime` 필드 추가
- `POST /api/admin/users/{id}/suspend` — 30일 정지 (`suspendedUntil = now + 30d`, `status = SUSPENDED`)
- `POST /api/admin/users/{id}/terminate` — 즉시 영구정지 (약관 제12조 2항)
- `POST /api/admin/users/{id}/release` — 정지 해제
- **자동해제 스케줄러** `@Scheduled(cron = "0 0 * * * *")` 시간별 스캔
  - `suspendedUntil < now` → `WITHDRAWN` (약관: 30일 후 자동 해지)
- **로그인 차단** `JwtAuthenticationFilter` 또는 별도 필터에서 `SUSPENDED` 상태 유저 401 처리
- `@LogAccess("USER_SUSPEND"/"USER_TERMINATE"/"USER_RELEASE")`

#### T8. 문의 처리 상태
- `Inquiry`에 필드 추가:
  ```
  status: InquiryStatus (RECEIVED, IN_PROGRESS, ANSWERED)
  answer: String (nullable)
  answeredAt: LocalDateTime
  ```
- `GET /api/admin/inquiries?status=` — 목록
- `PATCH /api/admin/inquiries/{id}/status` — 상태·답변 저장
- 개인정보 포함 문의 조회 시 `@LogAccess("INQUIRY_VIEW_PII")` — 개인정보 포함 여부는 우선 관리자 판단(수동 플래그), 자동판정은 YAGNI

---

### 🟢 P2 — 운영 편의

#### T9. 관리자 위반 게시물 삭제
- `DELETE /api/admin/companion-posts/{id}` — 관리자 삭제 경로
- 회원 직접 삭제(F-BE-15) · 지난 공연 자동삭제(F-BE-31)는 별개 유지
- `@LogAccess("COMPANION_POST_DELETE")` — 삭제 사유(`reason` query/body) 함께 저장

#### T10. 관리자 계정 관리
- `GET /api/admin/admins` — 관리자 목록 (User where role=ADMIN)
- `POST /api/admin/admins/{userId}` — 유저를 ADMIN으로 승격
- `DELETE /api/admin/admins/{userId}` — ADMIN 해제
- 초기 관리자는 seed 또는 SQL 직접 (부트스트랩 문제)

---

### 📄 P3 — 문서

#### T11. 약관·처리방침 개정
- 이용약관: 신고 건 채팅 열람 권한 조항 부활
- 개인정보처리방침: 신고 처리 목적 채팅 조회 근거 명시
- **관리자 페이지 개발 완료 후 반드시 반영**

---

## 3. 인프라·운영 (코드 밖)

- **DB 직접 접속 차단** (처리방침 제10조 6항)
  - 프로덕션 DB 계정 최소화 (관리자 페이지 서비스 계정 외 조회 계정 없음)
  - 코드 변경 아님, 인프라 문서만 갱신

- **초기 ADMIN 부트스트랩**
  - 마이그레이션 SQL 또는 `CommandLineRunner`로 최초 PM 계정을 ADMIN으로 승격
  - 이후로는 T10 API로 관리

- **관리자 페이지 프론트 접근 제어**
  - 별도 도메인/서브도메인(예: `admin.loop-app.com`) 권장 (선택)
  - 최소한 프론트 라우팅에서 role 체크

---

## 4. 구현 순서 (권장)

```
T2 (ROLE)  →  T1 (GET 잠금)  →  T3 (접속기록 AOP)
                                    ↓
                          T4·T5·T6·T7·T8 병렬 가능
                                    ↓
                              T9·T10
                                    ↓
                              T11 (문서)
```

T2가 모든 후속 태스크의 전제. T3의 `@LogAccess`는 T4~T8 컨트롤러에서 즉시 사용.

---

## 5. 스킵/보류 결정 사항

| 항목 | 결정 | 이유 |
|---|---|---|
| 관리자 별도 테이블 | 스킵 | `User.role` 컬럼 하나로 충분. 100명 넘어가면 그때 |
| 이의제기 별도 테이블 | 스킵 | `Report.appealStatus` 두 상태로 커버. 접수는 이메일이라 시스템 밖 |
| 접속기록 아카이빙 | 스킵 | 1년 지나면 DELETE. 백업은 그때 검토 |
| 개인정보 자동 마스킹 | 스킵 | 관리자는 원본 봐야 함. 접속기록 남는 게 통제 수단 |
| PII 자동 판정 (문의) | 스킵 | 관리자 수동 플래그. 자동판정은 오탐 지옥 |
| AOP 대신 인터셉터 | AOP 선택 | 어노테이션 명시성 > 전체 자동 (실수 방지) |

---

## 6. 검증 체크리스트 (기능 완료 판정)

- [ ] 비관리자 계정이 `/api/admin/**` 호출 시 403
- [ ] 관리자 계정이 회원 조회 시 `admin_access_logs`에 레코드 남음
- [ ] 신고 상태 변경 시 이력 조회 가능
- [ ] 신고 관련 채팅 열람 시 양측 회원에게 알림 도달
- [ ] 30일 정지 후 스케줄러 실행 시 자동 `WITHDRAWN`
- [ ] 접속기록 1년 초과 데이터 배치 삭제
- [ ] 원격 `main/Admin` push 완료
