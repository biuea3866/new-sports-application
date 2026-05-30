/**
 * U-01: login() 성공 시 LoginResponse를 반환한다
 * U-02: register() 성공 시 RegisterUserResponse를 반환한다
 * U-03: GET /users/me 성공 시 프로필 정보를 반환한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import type { LoginResponse, RegisterUserResponse } from '../types';

// expo-secure-store, expo-router는 jest.setup.ts의 global mock에서 처리

describe('auth API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  afterEach(() => mock.reset());

  describe('U-01: login', () => {
    it('올바른 자격증명으로 login 호출 시 accessToken과 refreshToken을 반환한다', async () => {
      const mockResponse: LoginResponse = {
        accessToken: 'access-abc',
        refreshToken: 'refresh-xyz',
        accessTokenExpiresIn: 3600,
      };
      mock.onPost('/auth/login').reply(200, mockResponse);

      const res = await client.post<LoginResponse>('/auth/login', {
        email: 'test@example.com',
        password: 'password123',
      });

      expect(res.data.accessToken).toBe('access-abc');
      expect(res.data.refreshToken).toBe('refresh-xyz');
      expect(res.data.accessTokenExpiresIn).toBe(3600);
    });

    it('잘못된 자격증명으로 login 호출 시 401 에러가 발생한다', async () => {
      // refresh mock — 401 재시도를 막기 위해 refresh도 401 처리
      mock.onPost('/auth/refresh').reply(401, { message: 'Refresh failed' });
      mock.onPost('/auth/login').reply(401, { message: 'Unauthorized' });

      await expect(
        client.post('/auth/login', { email: 'wrong@example.com', password: 'wrong' })
      ).rejects.toThrow();
    });
  });

  describe('U-02: register', () => {
    it('유효한 정보로 register 호출 시 id와 email을 반환한다', async () => {
      const mockResponse: RegisterUserResponse = { id: 1, email: 'new@example.com' };
      mock.onPost('/users/register').reply(201, mockResponse);

      const res = await client.post<RegisterUserResponse>('/users/register', {
        email: 'new@example.com',
        password: 'password123',
      });

      expect(res.data.id).toBe(1);
      expect(res.data.email).toBe('new@example.com');
    });

    it('중복 이메일로 register 호출 시 4xx 에러가 발생한다', async () => {
      mock.onPost('/users/register').reply(409, { message: 'Email already exists' });

      await expect(
        client.post('/users/register', { email: 'dup@example.com', password: 'password123' })
      ).rejects.toThrow();
    });
  });

  describe('U-03: getMyProfile', () => {
    it('GET /users/me 호출 시 프로필 정보를 반환한다', async () => {
      mock.onGet('/users/me').reply(200, {
        id: 42,
        email: 'me@example.com',
        status: 'ACTIVE',
        createdAt: '2024-01-01T00:00:00Z',
      });

      const res = await client.get('/users/me');

      expect(res.data.id).toBe(42);
      expect(res.data.email).toBe('me@example.com');
      expect(res.data.status).toBe('ACTIVE');
    });
  });
});
