/**
 * room-format — 채팅방 목록(S1)·채팅방 대화(S2)가 공용으로 쓰는 순수 표시 유틸.
 *
 * 방 이름은 `RoomResponse.name`이 null일 수 있어(1:1 방은 이름을 두지 않는다) 방 종류로
 * 기본 이름을 만든다. 목록과 대화 화면이 서로 다른 이름을 보여주지 않도록 이 함수를 SSOT로
 * 공유한다(대화 화면 헤더가 "채팅" 고정 문구를 쓰던 문제의 재발 방지).
 */
import type { RoomResponse } from '../api/types';

const DIRECT_ROOM_FALLBACK_NAME = '1:1 채팅';
const GROUP_ROOM_FALLBACK_NAME = '그룹 채팅';

export function resolveRoomDisplayName(
  room: Pick<RoomResponse, 'name' | 'type'> | undefined | null
): string {
  if (room === undefined || room === null) {
    return GROUP_ROOM_FALLBACK_NAME;
  }
  if (room.name !== null && room.name !== undefined && room.name.trim().length > 0) {
    return room.name;
  }
  return room.type === 'DIRECT' ? DIRECT_ROOM_FALLBACK_NAME : GROUP_ROOM_FALLBACK_NAME;
}
