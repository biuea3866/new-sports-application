/**
 * user.ts — 사용자 API 함수
 */
import { getBeClient } from './be-client';
import type { MyProfileResponse } from './types';

export async function getMyProfile(): Promise<MyProfileResponse> {
  const res = await getBeClient().get<MyProfileResponse>('/users/me');
  return res.data;
}
