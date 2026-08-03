/**
 * user.ts — 사용자 API 함수
 */
import { getBeClient } from './be-client';
import type { MyProfileResponse } from './types';

export async function getMyProfile(): Promise<MyProfileResponse> {
  const res = await getBeClient().get<MyProfileResponse>('/users/me');
  return res.data;
}

/** `PATCH /users/me/nickname` — 마이페이지 닉네임 수정. */
export async function changeMyNickname(nickname: string): Promise<MyProfileResponse> {
  const res = await getBeClient().patch<MyProfileResponse>('/users/me/nickname', { nickname });
  return res.data;
}
