/**
 * users.ts — 사용자 도메인 API 함수
 *
 * BE 경로:
 *   GET  /users/me       — 내 프로필 조회
 *   POST /users/register — 회원가입
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export interface UserDto {
  id: number;
  email: string;
  name: string;
  phone: string | null;
  profileImageUrl: string | null;
  createdAt: string;
}

export interface RegisterUserRequest {
  email: string;
  password: string;
  name: string;
  phone?: string;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function getMe(): Promise<UserDto> {
  const response = await getBeClient().get<UserDto>(PATHS.usersMe);
  return response.data;
}

export async function registerUser(request: RegisterUserRequest): Promise<UserDto> {
  const response = await getBeClient().post<UserDto>(PATHS.usersRegister, request);
  return response.data;
}
