/**
 * auth.ts — 인증 API 함수
 * getBeClient()를 통해 BE /auth, /users 엔드포인트 호출.
 */
import { getBeClient } from './be-client';
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterUserResponse,
} from './types';

export async function login(body: LoginRequest): Promise<LoginResponse> {
  const res = await getBeClient().post<LoginResponse>('/auth/login', body);
  return res.data;
}

export async function register(body: RegisterRequest): Promise<RegisterUserResponse> {
  const res = await getBeClient().post<RegisterUserResponse>('/users/register', body);
  return res.data;
}
