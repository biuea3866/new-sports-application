/**
 * useCurrentUserId — 현재 로그인 사용자 id 확정.
 *
 * 회귀 방지: 임시 스텁이 사용자와 무관하게 고정 id(1)를 돌려줘 장바구니·한정판 구매·
 * 대기열이 남의 계정으로 동작했다. 서버 프로필(GET /users/me)이 유일한 기준이어야 한다.
 */
import { renderHook } from '@testing-library/react-native';

jest.mock('../useMyProfile', () => ({
  useMyProfile: jest.fn(),
}));

import { useMyProfile } from '../useMyProfile';
import { useCurrentUserId } from '../useCurrentUserId';

const useMyProfileMock = useMyProfile as unknown as jest.Mock;

describe('useCurrentUserId', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('서버 프로필의 사용자 id를 반환한다', () => {
    useMyProfileMock.mockReturnValue({
      data: {
        id: 68,
        email: 'demo.user@sportsapp.dev',
        status: 'ACTIVE',
        createdAt: '2026-07-31T05:00:00Z',
      },
      isLoading: false,
      isError: false,
    });

    const { result } = renderHook(() => useCurrentUserId());

    expect(result.current).toBe(68);
  });

  it('프로필을 아직 못 받았으면 0을 반환한다(조회 게이팅용)', () => {
    useMyProfileMock.mockReturnValue({ data: undefined, isLoading: true, isError: false });

    const { result } = renderHook(() => useCurrentUserId());

    expect(result.current).toBe(0);
  });

  it('프로필 조회에 실패해도 고정 id로 대체하지 않는다', () => {
    useMyProfileMock.mockReturnValue({ data: undefined, isLoading: false, isError: true });

    const { result } = renderHook(() => useCurrentUserId());

    expect(result.current).toBe(0);
  });
});
