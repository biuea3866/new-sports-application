-- V55: rooms — host_user_id 컬럼 추가 (BE-13 채팅 방 host 모델 통일)
-- 배경: GuestInvitationDomainService(활성 MEMBER 중 joinedAt 최솟값 1명)와 GuestEvictionDomainService
--   (활성 MEMBER 전원)가 서로 다른 규칙으로 방장을 추론해 권한 비대칭이 있었다(2번째로 참여한 MEMBER는
--   게스트 방출은 되는데 초대는 안 됨). rooms.host_user_id를 도입해 방장을 명시적으로 영속하고, 두
--   서비스 모두 Room.requireHostedBy(userId) 단일 판정으로 통일한다.
-- 락 영향: ADD COLUMN(nullable, DEFAULT 없음) = MySQL 8.0 INSTANT 가능(락 없음).
-- 백필: 기존 각 방의 host_user_id 를 "활성(deleted_at IS NULL) MEMBER 중 joined_at 최솟값 참여자의
--   user_id"로 채운다 — 기존 GuestInvitationDomainService.requireHost 규칙과 동일해 기존 초대 권한이
--   변하지 않는다. 동일 joined_at 이 여럿이면 user_id 최솟값으로 결정성을 부여한다(기존 Kotlin
--   `minByOrNull`도 동률 시 순서가 임의였으므로 동작 의미상 문제 없음). DIRECT 방·MEMBER 참여자가 없는
--   방(전원 GUEST 등)은 host_user_id 가 NULL로 남는다 — host 액션(초대/방출) 비대상이라 문제 없음.
-- 배포 순서: 스키마 먼저(본 마이그레이션, nullable 추가+백필) → 코드(Room.hostUserId/requireHostedBy,
--   GuestInvitationDomainService/GuestEvictionDomainService/RoomContextDomainService/MessageDomainService,
--   CreateRoomUseCase) → 무중단. contract(NOT NULL 전환)는 이번 범위 밖(후속 과제).
-- 번호 재검사: 머지 직전 `ls db/migration | grep -oE '^V[0-9]+' | sort -n | uniq -d`로 중복 여부 재확인.
-- 롤백(역방향 DDL): 아래를 그대로 실행한다.
--   ALTER TABLE rooms DROP COLUMN host_user_id;

ALTER TABLE rooms
    ADD COLUMN host_user_id BIGINT NULL COMMENT '방장 user_id — FK 금지(일반 BIGINT). 방장 개념이 없거나 아직 지정되지 않은 방(DIRECT 등)은 NULL' AFTER context_id;

UPDATE rooms r
    INNER JOIN (
        SELECT rp.room_id, MIN(rp.user_id) AS host_user_id
        FROM room_participants rp
        INNER JOIN (
            SELECT room_id, MIN(joined_at) AS min_joined_at
            FROM room_participants
            WHERE participant_type = 'MEMBER' AND deleted_at IS NULL
            GROUP BY room_id
        ) earliest ON earliest.room_id = rp.room_id AND earliest.min_joined_at = rp.joined_at
        WHERE rp.participant_type = 'MEMBER' AND rp.deleted_at IS NULL
        GROUP BY rp.room_id
    ) host_map ON host_map.room_id = r.id
    SET r.host_user_id = host_map.host_user_id
    WHERE r.deleted_at IS NULL;
