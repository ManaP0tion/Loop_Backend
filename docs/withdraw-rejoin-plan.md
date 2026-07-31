# 탈퇴/재가입 시 채팅 숨김 & 제재 지속 계획

## 배경 · 요구사항

1. **채팅 기록 숨김**: 탈퇴하면 해당 사용자의 채팅은 **DB에는 남기되**, 같은 계정으로 **재가입했을 때 옛 채팅 기록이 보이면 안 됨**.
2. **제재 지속**: 영구 차단·정지(제재)를 받은 계정은 **재가입해도 제재가 유지**되어야 함.

## 현재 구조 (사실관계)

- 재가입은 새 계정을 만들지 않는다. 카카오 로그인 시 `(auth_provider, provider_id)`로 기존 row를 찾고, `WITHDRAWN`이면 `User.reactivate()`로 **같은 User id를 되살린다**. → 재가입 = 동일 식별자.
- 탈퇴(`UserServiceImpl.withdrawUser`)는 companionPost/heart/hashtag/favoriteArtist만 삭제하고, **`sanction_records`·`Block`·채팅(room/message/participant)은 보존**한다.
- `User.withdraw()`는 PII만 지우고 `auth_provider/provider_id/suspended_until`은 남긴다.

## 요구사항 2 (제재 지속) — 이미 성립, 확인만

이미 아래 이유로 재가입해도 제재가 유지된다. **코드 변경 불필요.**

- 재가입은 같은 User row를 재사용하므로 `sanction_records`(제재 이력)와 `suspended_until`이 그대로 따라온다.
- 정지 상태(`SUSPENDED`) 계정은 `JwtAuthenticationFilter`에서 모든 요청이 403으로 막히므로 **정지 중에는 탈퇴 자체가 불가**. 즉 "정지 → 탈퇴 → 재가입으로 회피" 경로가 닫혀 있다.
- 재가입(로그인) 시 매칭된 row가 `SUSPENDED`(만료 전/영구)면 `login()`에서 `USER_SUSPENDED`로 로그인 차단.

> 참고(선택): 정지 시점에 해당 계정의 Access Token을 블랙리스트에 넣으면 "정지 직전 발급된 토큰으로 탈퇴" 같은 극단 케이스까지 차단된다. 현재도 필터가 요청마다 DB status를 확인하므로 실질 위험은 없음. **지금은 추가 안 함.**

## 요구사항 1 (채팅 숨김) — 구현 필요

### 문제점

- **A.** `ChatServiceImpl.handleUserWithdrawn`은 시스템 메시지만 브로드캐스트하고 탈퇴자의 `ChatParticipant`를 `LEFT`로 바꾸지 않는다. 재가입(같은 id) 후 `getMyRooms`의 `findActiveRoomsByUserId`가 옛 방들을 그대로 반환 → **방 목록에 옛 채팅이 노출**.
- **B.** 재가입 후 같은 상대와 다시 대화를 시작하면 `startDirectChat`가 페어당 1개 방을 재사용(`rejoin()`)하는데, `getMessages`에 컷오프가 없어 **재가입 이전 메시지까지 전부 조회**된다.
- 단, 일반 나가기/재입장(LINE식 hide/rejoin)은 기존처럼 히스토리를 유지해야 하므로, 숨김은 **탈퇴→재가입 경계에서만** 적용해야 한다.

### 설계 (구현 완료 — 나가기 + 컷오프)

탈퇴 시 모든 방에서 나가고(`leave()`), 재가입 시각을 컷오프로 저장해 그 이전 메시지/방을 조회에서 제외한다. 메시지·participant row는 DB에 그대로 남는다.

1. **`users.chat_hidden_before` 컬럼 추가** (`LocalDateTime`, nullable).
   - `User.reactivate()`에서 `this.chatHiddenBefore = LocalDateTime.now()` 설정.

2. **탈퇴 시 방에서 나가기** — `handleUserWithdrawn`에서 시스템 메시지 브로드캐스트 후, 탈퇴자의 `ACTIVE` `ChatParticipant`를 모두 `leave()`.
   - 효과: 방 목록에서 사라지고(문제 A), 상대방은 `OTHER_USER_LEFT`로 인지. room/message row는 보존.

3. **메시지 조회 컷오프** — `getMessages`에서 `chatHiddenBefore`가 있으면 `createdAt > cutoff` 메시지만 반환(문제 B, 재대화로 방 재사용 시 옛 메시지 숨김).
   - 리포지토리 `findByChatRoom_IdAndCreatedAtAfterOrderByCreatedAtDesc(...)`, `cutoff == null`이면 기존 메서드.

4. **방 목록 컷오프** — `getMyRooms`에서 컷오프 이후 마지막 메시지를 조회하고, 없으면 목록에서 제외.
   - 리포지토리 `findTopByChatRoom_IdAndCreatedAtAfterOrderByCreatedAtDesc(...)` 추가. (2번 나가기로 대부분 커버되지만, 재대화로 되살린 방의 미리보기까지 방어)

> ponytail: `getMyRooms`의 `unreadCount`는 컷오프 미반영(전체 unread 집계). 재가입 후 옛 unread가 뱃지에 잡힐 수 있으나 히스토리 노출은 아님. 뱃지 정확도 필요 시 컷오프용 count 쿼리 추가.

### 변경 파일

- `User/domain/User.java` — `chatHiddenBefore` 필드 + `reactivate()`에서 세팅.
- `Chat/service/ChatServiceImpl.java` — `handleUserWithdrawn`에 `leave()` 추가, `getMessages`·`getMyRooms`에 컷오프 분기.
- `Chat/repository/MessageRepository.java` — 컷오프 조회 메서드 2개 추가.
- DDL: `ALTER TABLE users ADD COLUMN chat_hidden_before datetime NULL;` (JPA `ddl-auto: update`라 자동 생성됨).

### 검증

- 탈퇴 → 상대방 방에서 "탈퇴했어요" 표시 + 상대가 더 못 보냄(`OTHER_USER_LEFT`), 방은 상대에게만 잔존.
- 같은 계정 재가입 → `getMyRooms` 빈 목록.
- 재가입 후 같은 상대와 재대화 → 방 재사용되지만 `getMessages`는 재가입 이후 메시지만 반환(옛 메시지는 DB엔 존재).
- 일반 나가기→재입장(탈퇴 아님)은 컷오프가 null이라 히스토리 그대로 유지.

## 요약

- 요구사항 2: 변경 없음(이미 성립). 문서로 근거만 남김.
- 요구사항 1: `chat_hidden_before` 컷오프 1개 + 탈퇴 시 `leave()` + 조회 필터. 신규 테이블/추상화 없음.
