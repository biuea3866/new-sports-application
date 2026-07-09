/**
 * U-01: listRecruitments는 GET /recruitments를 호출하고 목록을 반환한다(communityId 필터 포함)
 * U-02: getRecruitment는 GET /recruitments/{id}로 상세를 반환한다
 * U-03: createRecruitment는 POST /recruitments로 생성된 모집을 반환한다
 * U-04: listApplications는 GET /recruitments/{id}/applications로 신청자 목록을 반환한다
 * U-05: applyRecruitment는 POST /recruitments/{id}/applications로 신청 결과를 반환한다
 * U-06: applyRecruitment는 무료(fee=0) 신청이면 checkoutUrl이 null이다
 * U-07: cancelRecruitment는 POST /recruitments/{id}/cancel을 호출한다
 * U-08: listMyApplications는 GET /applications로 본인 신청 목록을 반환한다
 * U-09: cancelApplication은 POST /applications/{id}/cancel을 호출한다
 * U-10: 정원초과(409)·권한없음(403)·마감후(422) 에러가 예외로 전파된다
 * U-11: getApplicationDetail은 GET /applications/{id}로 신청 상세를 반환한다(주문상세 Option A)
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import {
  applyRecruitment,
  cancelApplication,
  cancelRecruitment,
  createRecruitment,
  getApplicationDetail,
  getRecruitment,
  listApplications,
  listMyApplications,
  listRecruitments,
} from '../recruitment';
import type {
  ApplicationDetailResponse,
  ApplicationResponse,
  ApplyRecruitmentRequest,
  ApplyRecruitmentResult,
  CreateRecruitmentRequest,
  RecruitmentResponse,
} from '../recruitment';

jest.mock('../be-client', () => {
  const actual = jest.requireActual<typeof import('../be-client')>('../be-client');
  const instance = actual.createBeClient('http://localhost:8080');
  return {
    ...actual,
    getBeClient: jest.fn(() => instance),
    _testInstance: instance,
  };
});

import * as beClientModule from '../be-client';

const testInstance = (
  beClientModule as unknown as { _testInstance: ReturnType<typeof createBeClient> }
)._testInstance;
const mock = new MockAdapter(testInstance);

afterEach(() => mock.reset());

const mockRecruitment: RecruitmentResponse = {
  id: 1,
  title: '주말 축구 3명 모집',
  description: '한강공원에서 진행합니다',
  capacity: 3,
  feeAmount: 5000,
  activityAt: '2026-07-12T14:00:00+09:00',
  applicationDeadline: '2026-07-10T23:00:00+09:00',
  communityId: 7,
  recruiterUserId: 10,
  status: 'OPEN',
};

describe('U-01: listRecruitments', () => {
  it('communityId 없이 호출하면 전체 모집 목록을 반환한다', async () => {
    mock.onGet('/recruitments').reply(200, [mockRecruitment]);

    const res = await listRecruitments();

    expect(res).toHaveLength(1);
    expect(res[0].title).toBe('주말 축구 3명 모집');
  });

  it('communityId를 쿼리 파라미터로 전달한다', async () => {
    mock.onGet('/recruitments').reply((config) => {
      expect(config.params).toEqual({ communityId: 7 });
      return [200, [mockRecruitment]];
    });

    const res = await listRecruitments(7);

    expect(res[0].communityId).toBe(7);
  });

  it('모집 0건이면 빈 배열을 반환한다', async () => {
    mock.onGet('/recruitments').reply(200, []);

    const res = await listRecruitments();

    expect(res).toHaveLength(0);
  });
});

describe('U-02: getRecruitment', () => {
  it('GET /recruitments/1 호출 시 모집 상세를 반환한다', async () => {
    mock.onGet('/recruitments/1').reply(200, mockRecruitment);

    const res = await getRecruitment(1);

    expect(res.capacity).toBe(3);
    expect(res.status).toBe('OPEN');
  });

  it('U-10 존재하지 않는 모집(404)은 예외로 전파된다', async () => {
    mock.onGet('/recruitments/999').reply(404, { message: 'Not found' });

    await expect(getRecruitment(999)).rejects.toThrow();
  });
});

describe('U-03: createRecruitment', () => {
  it('POST /recruitments 호출 시 생성된 모집을 반환한다', async () => {
    mock.onPost('/recruitments').reply(200, mockRecruitment);

    const body: CreateRecruitmentRequest = {
      title: '주말 축구 3명 모집',
      description: '한강공원에서 진행합니다',
      capacity: 3,
      feeAmount: 5000,
      activityAt: '2026-07-12T14:00:00+09:00',
      applicationDeadline: '2026-07-10T23:00:00+09:00',
      communityId: 7,
    };
    const res = await createRecruitment(body);

    expect(res.id).toBe(1);
    expect(JSON.parse(mock.history.post[0].data as string)).toMatchObject({
      title: '주말 축구 3명 모집',
      capacity: 3,
    });
  });

  it('U-10 정원<1 등 검증 실패(400)는 예외로 전파된다', async () => {
    mock.onPost('/recruitments').reply(400, { message: 'capacity must be positive' });

    await expect(
      createRecruitment({
        title: 't',
        capacity: 0,
        feeAmount: 0,
        activityAt: '2026-07-12T14:00:00+09:00',
        applicationDeadline: '2026-07-10T23:00:00+09:00',
      })
    ).rejects.toThrow();
  });
});

describe('U-04: listApplications', () => {
  it('GET /recruitments/1/applications 호출 시 신청자 목록을 반환한다', async () => {
    const applications: ApplicationResponse[] = [
      {
        id: 100,
        recruitmentId: 1,
        status: 'CONFIRMED',
        paymentId: 200,
        appliedAt: '2026-07-08T00:00:00Z',
      },
    ];
    mock.onGet('/recruitments/1/applications').reply(200, applications);

    const res = await listApplications(1);

    expect(res).toHaveLength(1);
    expect(res[0].status).toBe('CONFIRMED');
  });

  it('신청자 0건이면 빈 배열을 반환한다(정상)', async () => {
    mock.onGet('/recruitments/1/applications').reply(200, []);

    const res = await listApplications(1);

    expect(res).toHaveLength(0);
  });

  it('U-10 개설자가 아니면 403 에러가 발생한다', async () => {
    mock.onGet('/recruitments/1/applications').reply(403, { message: 'Forbidden' });

    await expect(listApplications(1)).rejects.toThrow();
  });
});

describe('U-05 · U-06: applyRecruitment', () => {
  it('결제가 필요한 신청은 checkoutUrl·paymentId를 포함한 결과를 반환한다', async () => {
    const result: ApplyRecruitmentResult = {
      id: 100,
      recruitmentId: 1,
      status: 'PENDING',
      paymentId: 200,
      checkoutUrl: 'https://mock-pg.example.com/checkout/abc',
      appliedAt: '2026-07-08T00:00:00Z',
    };
    mock.onPost('/recruitments/1/applications').reply(202, result);

    const body: ApplyRecruitmentRequest = { paymentMethod: 'TOSS', currency: 'KRW' };
    const res = await applyRecruitment(1, body);

    expect(res.checkoutUrl).toBe('https://mock-pg.example.com/checkout/abc');
    expect(res.status).toBe('PENDING');
  });

  it('U-06 무료(fee=0) 신청은 checkoutUrl이 null이고 즉시 CONFIRMED다', async () => {
    const result: ApplyRecruitmentResult = {
      id: 101,
      recruitmentId: 2,
      status: 'CONFIRMED',
      paymentId: null,
      checkoutUrl: null,
      appliedAt: '2026-07-08T00:00:00Z',
    };
    mock.onPost('/recruitments/2/applications').reply(202, result);

    const res = await applyRecruitment(2, { paymentMethod: 'TOSS', currency: 'KRW' });

    expect(res.checkoutUrl).toBeNull();
    expect(res.status).toBe('CONFIRMED');
  });

  it('U-10 정원초과(409)는 예외로 전파된다', async () => {
    mock.onPost('/recruitments/1/applications').reply(409, { message: 'Recruitment is full' });

    await expect(applyRecruitment(1, { paymentMethod: 'TOSS', currency: 'KRW' })).rejects.toThrow();
  });
});

describe('U-07: cancelRecruitment', () => {
  it('POST /recruitments/1/cancel 호출 시 CANCELLED 상태의 모집을 반환한다', async () => {
    mock.onPost('/recruitments/1/cancel').reply(200, { ...mockRecruitment, status: 'CANCELLED' });

    const res = await cancelRecruitment(1);

    expect(res.status).toBe('CANCELLED');
  });

  it('U-10 개설자가 아니면 403 에러가 발생한다', async () => {
    mock.onPost('/recruitments/1/cancel').reply(403, { message: 'Not the recruiter' });

    await expect(cancelRecruitment(1)).rejects.toThrow();
  });
});

describe('U-08: listMyApplications', () => {
  it('GET /applications 호출 시 본인 신청 목록을 반환한다', async () => {
    const applications: ApplicationResponse[] = [
      {
        id: 100,
        recruitmentId: 1,
        status: 'PENDING',
        paymentId: 200,
        appliedAt: '2026-07-08T00:00:00Z',
      },
    ];
    mock.onGet('/applications').reply(200, applications);

    const res = await listMyApplications();

    expect(res).toHaveLength(1);
  });

  it('신청 내역 0건이면 빈 배열을 반환한다', async () => {
    mock.onGet('/applications').reply(200, []);

    const res = await listMyApplications();

    expect(res).toHaveLength(0);
  });
});

describe('U-09: cancelApplication', () => {
  it('POST /applications/100/cancel 호출 시 CANCELLED 상태를 반환한다', async () => {
    mock.onPost('/applications/100/cancel').reply(200, {
      id: 100,
      recruitmentId: 1,
      status: 'CANCELLED',
      paymentId: 200,
      appliedAt: '2026-07-08T00:00:00Z',
    });

    const res = await cancelApplication(100);

    expect(res.status).toBe('CANCELLED');
  });

  it('U-10 마감 이후 취소 시도(422)는 예외로 전파된다', async () => {
    mock.onPost('/applications/100/cancel').reply(422, { message: 'Application deadline passed' });

    await expect(cancelApplication(100)).rejects.toThrow();
  });
});

describe('U-11: getApplicationDetail', () => {
  it('GET /applications/100 호출 시 신청 상세(모집명 포함)를 반환한다', async () => {
    const detail: ApplicationDetailResponse = {
      applicationId: 100,
      recruitmentId: 1,
      title: '주말 축구 3명 모집',
      status: 'CONFIRMED',
      feeAmount: 5000,
      paymentId: 200,
      createdAt: '2026-07-08T00:00:00Z',
    };
    mock.onGet('/applications/100').reply(200, detail);

    const res = await getApplicationDetail(100);

    expect(res.title).toBe('주말 축구 3명 모집');
    expect(res.recruitmentId).toBe(1);
  });

  it('신설 전(main 미머지) 404는 예외로 전파된다 — API 미연동 상태', async () => {
    mock.onGet('/applications/999').reply(404, { message: 'Not found' });

    await expect(getApplicationDetail(999)).rejects.toThrow();
  });
});
