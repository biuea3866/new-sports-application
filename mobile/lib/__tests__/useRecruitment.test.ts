/**
 * useRecruitments가 communityId 필터로 모집 목록을 반환한다
 * useRecruitment가 id로 모집 상세를 반환한다
 * useCreateRecruitment 성공 시 모집 목록 캐시가 무효화된다
 * useApplications가 신청자 목록을 반환한다(개설자 뷰)
 * useApplyRecruitment 성공 시 모집 상세·내 신청 목록 캐시가 무효화된다
 * useMyApplications가 본인 신청 목록을 반환한다
 * useCancelApplication 성공 시 내 신청 목록 캐시가 무효화된다
 * useCancelRecruitment 성공 시 모집 상세·목록 캐시가 무효화된다
 * 정원초과(409) 등 실패는 에러 상태로 전파된다
 */
import { createElement } from 'react';
import { act } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import {
  applicationsQueryKey,
  recruitmentQueryKey,
  recruitmentsQueryKey,
  useApplications,
  useApplyRecruitment,
  useCancelApplication,
  useCancelRecruitment,
  useCreateRecruitment,
  useMyApplications,
  useRecruitment,
  useRecruitments,
  MY_APPLICATIONS_QUERY_KEY,
  RECRUITMENTS_QUERY_KEY,
} from '../useRecruitment';
import type {
  ApplicationResponse,
  RecruitmentApplicantResponse,
  ApplyRecruitmentResult,
  RecruitmentResponse,
} from '../../api/recruitment';

jest.mock('../../api/recruitment', () => ({
  listRecruitments: jest.fn(),
  getRecruitment: jest.fn(),
  createRecruitment: jest.fn(),
  listApplications: jest.fn(),
  applyRecruitment: jest.fn(),
  cancelRecruitment: jest.fn(),
  listMyApplications: jest.fn(),
  cancelApplication: jest.fn(),
}));

import {
  applyRecruitment,
  cancelApplication,
  cancelRecruitment,
  createRecruitment,
  getRecruitment,
  listApplications,
  listMyApplications,
  listRecruitments,
} from '../../api/recruitment';

const listRecruitmentsMock = listRecruitments as jest.MockedFunction<typeof listRecruitments>;
const getRecruitmentMock = getRecruitment as jest.MockedFunction<typeof getRecruitment>;
const createRecruitmentMock = createRecruitment as jest.MockedFunction<typeof createRecruitment>;
const listApplicationsMock = listApplications as jest.MockedFunction<typeof listApplications>;
const applyRecruitmentMock = applyRecruitment as jest.MockedFunction<typeof applyRecruitment>;
const cancelRecruitmentMock = cancelRecruitment as jest.MockedFunction<typeof cancelRecruitment>;
const listMyApplicationsMock = listMyApplications as jest.MockedFunction<typeof listMyApplications>;
const cancelApplicationMock = cancelApplication as jest.MockedFunction<typeof cancelApplication>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

const mockRecruitment: RecruitmentResponse = {
  id: 1,
  title: '주말 축구 3명 모집',
  description: null,
  capacity: 3,
  feeAmount: 5000,
  activityAt: '2026-07-12T14:00:00+09:00',
  applicationDeadline: '2026-07-10T23:00:00+09:00',
  communityId: 7,
  recruiterUserId: 10,
  status: 'OPEN',
};

describe('useRecruitments', () => {
  afterEach(() => jest.clearAllMocks());

  it('communityId 필터로 모집 목록을 반환한다', async () => {
    listRecruitmentsMock.mockResolvedValue([mockRecruitment]);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useRecruitments(7), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([mockRecruitment]);
    expect(listRecruitmentsMock).toHaveBeenCalledWith(7);
  });

  it('communityId 없이 호출하면 전체 목록 쿼리를 보낸다', async () => {
    listRecruitmentsMock.mockResolvedValue([mockRecruitment]);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useRecruitments(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(listRecruitmentsMock).toHaveBeenCalledWith(undefined);
  });
});

describe('useRecruitment', () => {
  afterEach(() => jest.clearAllMocks());

  it('id로 모집 상세를 반환한다', async () => {
    getRecruitmentMock.mockResolvedValue(mockRecruitment);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useRecruitment(1), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockRecruitment);
  });

  it('id가 0 이하면 쿼리를 실행하지 않는다', () => {
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useRecruitment(0), { wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(getRecruitmentMock).not.toHaveBeenCalled();
  });
});

describe('useCreateRecruitment', () => {
  afterEach(() => jest.clearAllMocks());

  it('성공 시 모집 목록 캐시가 무효화된다', async () => {
    createRecruitmentMock.mockResolvedValue(mockRecruitment);
    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useCreateRecruitment(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        title: '주말 축구 3명 모집',
        capacity: 3,
        feeAmount: 5000,
        activityAt: '2026-07-12T14:00:00+09:00',
        applicationDeadline: '2026-07-10T23:00:00+09:00',
        communityId: 7,
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: RECRUITMENTS_QUERY_KEY });
  });

  it('검증 실패(400) 시 에러 상태로 전파된다', async () => {
    createRecruitmentMock.mockRejectedValue(new Error('capacity must be positive'));
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCreateRecruitment(), { wrapper });

    await act(async () => {
      await expect(
        result.current.mutateAsync({
          title: 't',
          capacity: 0,
          feeAmount: 0,
          activityAt: '2026-07-12T14:00:00+09:00',
          applicationDeadline: '2026-07-10T23:00:00+09:00',
        })
      ).rejects.toThrow('capacity must be positive');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useApplications', () => {
  afterEach(() => jest.clearAllMocks());

  it('개설자 뷰의 신청자 목록을 반환한다', async () => {
    const applications: RecruitmentApplicantResponse[] = [
      {
        id: 100,
        recruitmentId: 1,
        applicantUserId: 71,
        applicantDisplayName: '김철수',
        status: 'CONFIRMED',
        appliedAt: '2026-07-08T00:00:00Z',
      },
    ];
    listApplicationsMock.mockResolvedValue(applications);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useApplications(1), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(applications);
    expect(listApplicationsMock).toHaveBeenCalledWith(1);
  });

  it('신청자 0건이면 빈 배열을 반환한다(정상)', async () => {
    listApplicationsMock.mockResolvedValue([]);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useApplications(1), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([]);
  });
});

describe('useApplyRecruitment', () => {
  afterEach(() => jest.clearAllMocks());

  it('성공 시 모집 상세·내 신청 목록 캐시가 무효화된다', async () => {
    const applyResult: ApplyRecruitmentResult = {
      id: 100,
      recruitmentId: 1,
      status: 'PENDING',
      paymentId: 200,
      checkoutUrl: 'https://mock-pg.example.com/checkout/abc',
      appliedAt: '2026-07-08T00:00:00Z',
    };
    applyRecruitmentMock.mockResolvedValue(applyResult);
    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useApplyRecruitment(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ paymentMethod: 'TOSS', currency: 'KRW' });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.checkoutUrl).toBe('https://mock-pg.example.com/checkout/abc');
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: recruitmentQueryKey(1) });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: MY_APPLICATIONS_QUERY_KEY });
  });

  it('정원초과(409) 시 에러 상태로 전파된다', async () => {
    applyRecruitmentMock.mockRejectedValue(
      Object.assign(new Error('Recruitment is full'), { status: 409 })
    );
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useApplyRecruitment(1), { wrapper });

    await act(async () => {
      await expect(
        result.current.mutateAsync({ paymentMethod: 'TOSS', currency: 'KRW' })
      ).rejects.toThrow('Recruitment is full');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useMyApplications', () => {
  afterEach(() => jest.clearAllMocks());

  it('본인 신청 목록을 반환한다', async () => {
    const applications: ApplicationResponse[] = [
      {
        id: 100,
        recruitmentId: 1,
        applicantUserId: 71,
        status: 'PENDING',
        paymentId: 200,
        appliedAt: '2026-07-08T00:00:00Z',
      },
    ];
    listMyApplicationsMock.mockResolvedValue(applications);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useMyApplications(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(applications);
  });
});

describe('useCancelApplication', () => {
  afterEach(() => jest.clearAllMocks());

  it('성공 시 내 신청 목록 캐시가 무효화된다', async () => {
    cancelApplicationMock.mockResolvedValue({
      id: 100,
      recruitmentId: 1,
      applicantUserId: 71,
      status: 'CANCELLED',
      paymentId: 200,
      appliedAt: '2026-07-08T00:00:00Z',
    });
    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useCancelApplication(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync(100);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(cancelApplicationMock).toHaveBeenCalledWith(100);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: MY_APPLICATIONS_QUERY_KEY });
  });

  it('마감 후 취소 시도(422) 시 에러 상태로 전파된다', async () => {
    cancelApplicationMock.mockRejectedValue(
      Object.assign(new Error('Application deadline passed'), { status: 422 })
    );
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCancelApplication(), { wrapper });

    await act(async () => {
      await expect(result.current.mutateAsync(100)).rejects.toThrow('Application deadline passed');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('useCancelRecruitment', () => {
  afterEach(() => jest.clearAllMocks());

  it('성공 시 모집 상세·목록 캐시가 무효화된다', async () => {
    cancelRecruitmentMock.mockResolvedValue({ ...mockRecruitment, status: 'CANCELLED' });
    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useCancelRecruitment(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync(1);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: recruitmentQueryKey(1) });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: RECRUITMENTS_QUERY_KEY });
  });

  it('개설자가 아니면(403) 에러 상태로 전파된다', async () => {
    cancelRecruitmentMock.mockRejectedValue(
      Object.assign(new Error('Not the recruiter'), { status: 403 })
    );
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCancelRecruitment(), { wrapper });

    await act(async () => {
      await expect(result.current.mutateAsync(1)).rejects.toThrow('Not the recruiter');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe('query key helpers', () => {
  it('recruitmentsQueryKey/applicationsQueryKey가 결정적으로 계산된다', () => {
    expect(recruitmentsQueryKey(7)).toEqual([...RECRUITMENTS_QUERY_KEY, 7]);
    expect(recruitmentsQueryKey()).toEqual([...RECRUITMENTS_QUERY_KEY, null]);
    expect(applicationsQueryKey(1)).toEqual(['recruitments', 1, 'applications']);
  });
});
