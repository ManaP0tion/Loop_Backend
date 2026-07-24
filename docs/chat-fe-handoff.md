# 채팅 BE 변경사항 (프론트 전달용)

브랜치: `chat` / 관련 요구사항: REQ-CHT-01 ~ 06, F-BE-19 ~ 24

---

## 1. 요약

- `POST /api/chat/direct` 요청/응답에 **공연/동행글/상대 유저 컨텍스트 필드 추가**
- **차단·신고·나가기·탈퇴** 시 채팅방 자동 hide + 상대 상태에 따른 **전송 가드**
- **시스템 메시지** (`SYSTEM_LEAVE` / `SYSTEM_WITHDRAWN`) 대화 이력에 남음
- **STOMP 개인 에러 채널** 신설 → 전송 실패 시 프론트가 토스트로 표시 가능

---

## 2. 두 채팅방 생성 API 정리

프론트는 **`/api/chat/direct` 만** 사용하시면 됩니다.

| 항목 | `/api/chat/rooms` (사용 X, 레거시) | `/api/chat/direct` (**사용**) |
|---|---|---|
| 요청 파라미터 | `postId` | `targetUserId` (필수) + `companionPostId` (선택) |
| 방 유일성 기준 | 동행글 1개당 방 1개 | 두 유저 사이 DIRECT 방 1개 (있으면 재사용) |
| 재요청 시 | 같은 postId면 409 에러 | 기존 방 재사용 |

---

## 3. `POST /api/chat/direct` 요청 DTO 변경

```json
{
  "targetUserId": 42,
  "companionPostId": 123
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `targetUserId` | Long | O | 채팅 상대 유저 ID |
| `companionPostId` | Long | **X (신규)** | 채팅을 시작한 상대방의 동행글 ID. 전달 시 방의 공연/동행글 컨텍스트를 최신 값으로 갱신 |

- 동행 프로필에서 채팅 진입 시 `companionPostId` 넘겨주시면 채팅방 상단 공연 카드가 그 컨텍스트로 갱신됨
- 매 호출마다 최신 값으로 덮어쓰기 (같은 상대와 여러 동행글에서 채팅 시도 가능)
- 서버 검증: `companionPostId` 의 작성자가 `targetUserId` 와 일치해야 함 (아니면 403)

---

## 4. 응답 DTO 신규 필드

### 4.1 `ChatRoomResponseDto` (아래 API 응답에 공통)
- `POST /api/chat/direct`
- `POST /api/chat/rooms` (레거시)
- `POST /api/chat/rooms/{roomId}/join` (레거시)
- `GET /api/chat/rooms/me` (목록)

| 신규 필드 | 타입 | 설명 |
|---|---|---|
| `otherUserId` | Long | 상대방 유저 ID. **탈퇴/삭제된 유저도 참여자 이력으로 채워짐** → 차단/신고 대상 식별에 사용 |
| `concertId` | Long? | 연결된 공연 ID (원본 동행글이 있으면 그 공연 ID, 없으면 null) — 상단 공연 카드용 |
| `otherCompanionId` | Long? | 연결된 상대방 동행글 ID (없으면 null) — 프로필 이미지 클릭 시 이동 대상 |

기존 필드 (`id`, `name`, `type`, `otherUserNickname`, `otherUserProfileImageUrl`, `otherUserGender`, `otherUserBirthDate`, `lastMessageContent`, `lastMessageAt`, `unreadCount`, `otherUserRelation`) 는 그대로.

### 4.2 `ChatMessagesResponseDto` (`GET /api/chat/rooms/{roomId}/messages`)

| 신규 필드 | 타입 | 설명 |
|---|---|---|
| `concertId` | Long? | 위와 동일 |
| `otherCompanionId` | Long? | 위와 동일 |

`otherUserRelation` 안의 `otherUserWithdrawn` / `blockedByMe` / `blockedMe` / `reportedByMe` 는 기존 유지.

---

## 5. 시스템 메시지 (신규)

`GET /api/chat/rooms/{roomId}/messages` 응답의 `messages[].type` 이 확장되었습니다.

| type | 언제 | senderId | content 예시 |
|---|---|---|---|
| `TALK` | 유저가 보낸 일반 메시지 (기존) | 발신자 ID | 유저가 입력한 문자열 |
| `SYSTEM_LEAVE` | **신규** — 상대가 채팅방 나가기 실행 | 나간 사람 ID | `"OO님이 채팅방을 나갔습니다"` |
| `SYSTEM_WITHDRAWN` | **신규** — 상대가 서비스 탈퇴 | 탈퇴한 사람 ID | `"OO님이 루프를 탈퇴했어요"` |

- 이력 조회, STOMP 실시간 브로드캐스트 (`/sub/chat/room/{roomId}`) 양쪽에 동일하게 나감
- FE 렌더링 권장: 말풍선 아닌 중앙 정렬 회색 텍스트
- `isRead` / `senderId` 는 채워져 있지만 안읽음 카운트에는 포함되지 않도록 서버에서 필터함

---

## 6. 전송 실패 케이스 & STOMP 에러 채널 (신규)

### 6.1 왜 필요한가

STOMP `SEND` (`/pub/chat/message`) 로 메시지 보낼 때 서버가 거절해도, 지금까지는 클라이언트가 아무 응답도 못 받음. 이제 **발신자 개인 에러 채널** 로 실패 이유가 흘러갑니다.

### 6.2 프론트 작업

1. STOMP 연결 후 **`/sub/chat/errors/{내 유저ID}`** 를 구독 (본인 ID 아니면 서버가 거절)
2. 수신 payload:

```json
{
  "code": "OTHER_USER_LEFT",
  "message": "상대가 채팅방을 나가 메시지를 전송할 수 없습니다.",
  "roomId": 42
}
```

3. `code` 로 분기해서 토스트 렌더링

### 6.3 나올 수 있는 `code` 목록

| code | 상황 | 권장 토스트 문구 (F-FE-30 기준) |
|---|---|---|
| `OTHER_USER_LEFT` | 상대가 채팅방을 나감 | "상대가 채팅방을 나가 전달할 수 없어요" |
| `OTHER_USER_WITHDRAWN` | 상대가 탈퇴 | "상대방이 루프를 탈퇴했어요" (또는 입력창 자체를 비활성화) |
| `BLOCKED_USER` | 나-상대 사이 차단 관계 존재 | "메시지를 전달할 수 없습니다" (사전 안내 없음) |
| `NOT_CHAT_PARTICIPANT` | 내가 참여자가 아님 (버그성) | (에러 로그만, 토스트 생략 or 일반 실패) |
| `MESSAGE_CONTENT_INVALID` | 빈 문자열 등 | "메시지 내용을 입력해주세요" |

`roomId` 는 실패한 SEND 의 방 ID. 여러 방을 동시에 열어둔 경우 어느 방에서 뜬 에러인지 구분용.

### 6.4 REST 는 그대로

`PATCH /api/chat/rooms/{roomId}/read`, `POST /api/chat/direct` 등 HTTP API 는 원래대로 응답 body 의 `CommonResponse` 실패 형태로 옵니다. 이 채널은 **STOMP SEND 실패 전용**.

---

## 7. 채팅방 hide 동작 (F-BE-21 ~ 23)

DB 는 **soft-hide** (참여자 상태 = `LEFT`). 원본 대화 이력은 관리자 감사 목적으로 보존.

| 행동 | 실행자 view | 상대 view |
|---|---|---|
| **나가기** (`PATCH /leave`) | 목록에서 사라짐 | 방/이력 유지 + `SYSTEM_LEAVE` 표시 + 전송 시 `OTHER_USER_LEFT` |
| **차단** (`POST /api/blocks`) | 목록에서 사라짐 (자동 hide) | 방/이력 유지 (사전 안내 없음, 전송 시 `BLOCKED_USER`) |
| **신고** (`POST /api/reports`) | 목록에서 사라짐 (자동 hide, `blockToo` 무관) | 방/이력 유지 |
| **탈퇴** (`POST /api/users/me/withdraw`) | 계정 자체 사라짐 | 방/이력 유지 + `SYSTEM_WITHDRAWN` 표시 + 전송 시 `OTHER_USER_WITHDRAWN` |

- 다시 같은 상대와 채팅 시작: 나(LEFT)/상대(ACTIVE) 상태에서 `POST /api/chat/direct` 하면 **새 방이 생성됨** (기존 방은 상대만 참조). 실질적으로 나에겐 대화 리셋 효과.
- 관리자 강제 종료 (`POST /admin/users/{id}/terminate`) 도 탈퇴와 동일 훅.

---

## 8. FE 체크리스트

- [ ] `POST /api/chat/direct` 호출 시 동행 프로필 진입인 경우 `companionPostId` 함께 전송
- [ ] 목록/방/메시지 화면에서 `otherUserId` 사용 (차단·신고 API 파라미터)
- [ ] 상단 공연 카드는 `concertId` 로 공연 API 조회
- [ ] 프로필 이미지 탭 → `otherCompanionId` 로 동행 프로필 페이지 이동
- [ ] 메시지 이력에서 `type === "SYSTEM_LEAVE" | "SYSTEM_WITHDRAWN"` 일 때 중앙 정렬 시스템 메시지 스타일 렌더링
- [ ] STOMP 연결 후 `/sub/chat/errors/{내 유저ID}` 구독 → `code` 별 토스트
- [ ] 탈퇴자 (`otherUserRelation.otherUserWithdrawn === true`) 헤더 닉네임/프로필 "알 수 없음" 익명화
- [ ] 채팅방 나가기 확인 모달, 차단 확인/완료 모달, 신고 완료 모달 문구는 F-FE-27/28/29 스펙 그대로

---

## 9. 파일 참조 (BE)

- 요청 DTO: `Chat/dto/StartDirectChatRequestDto.java`
- 응답 DTO: `Chat/dto/ChatRoomResponseDto.java`, `Chat/dto/ChatMessagesResponseDto.java`, `Chat/dto/ChatErrorDto.java`
- 시스템 메시지 도메인: `Chat/domain/MessageType.java`, `Chat/domain/Message.java`
- 서비스 로직: `Chat/service/ChatServiceImpl.java` (`saveMessage` 가드 / `hideDirectRoomForUser` / `handleUserWithdrawn`)
- STOMP 에러 핸들러: `Chat/controller/ChatController.java`
- 구독 가드: `Chat/interceptor/StompSubscriptionInterceptor.java`
- 훅 지점: `Block/service/BlockServiceImpl.java`, `Report/service/ReportServiceImpl.java`, `User/service/UserServiceImpl.java`, `Admin/controller/AdminController.java`
