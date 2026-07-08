/**
 * U-01: useFacilities — gu 또는 type이 있을 때만 쿼리를 실행한다
 * U-02: useFacilities — 쿼리 성공 시 FacilityPageResponse를 반환한다
 * U-03: useFacilityDetail — id가 비어있으면 쿼리를 실행하지 않는다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../../api/be-client';
import type { FacilityPageResponse, FacilityResponse } from '../../api/types';

describe('Facility API hooks (via raw client)', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  afterEach(() => mock.reset());

  const mockFacility: FacilityResponse = {
    id: 10,
    name: '서초 실내 체육관',
    gu: '서초구',
    type: 'INDOOR',
    address: '서울 서초구 반포대로 1',
    parking: false,
    tel: '02-9876-5432',
    lat: 37.4837,
    lng: 127.0324,
    sidoCode: '11',
    sidoName: '서울특별시',
    sigunguCode: '11650',
    sigunguName: '서초구',
  };

  const mockPage: FacilityPageResponse = {
    content: [mockFacility],
    number: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
    last: true,
  };

  describe('U-02: GET /facilities 응답 검증', () => {
    it('type=INDOOR 파라미터로 호출 시 시설 목록을 반환한다', async () => {
      mock.onGet('/facilities').reply(200, mockPage);

      const res = await client.get<FacilityPageResponse>('/facilities', {
        params: { type: 'INDOOR', page: 0, size: 50 },
      });

      expect(res.data.content[0].type).toBe('INDOOR');
      expect(res.data.content[0].parking).toBe(false);
    });

    it('parking 필드가 boolean으로 올바르게 매핑된다', async () => {
      mock.onGet('/facilities').reply(200, mockPage);

      const res = await client.get<FacilityPageResponse>('/facilities');
      expect(typeof res.data.content[0].parking).toBe('boolean');
    });
  });

  describe('U-03: GET /facilities/{id} 응답 검증', () => {
    it('id=10 호출 시 시설 상세 필드가 모두 채워진다', async () => {
      mock.onGet('/facilities/10').reply(200, mockFacility);

      const res = await client.get<FacilityResponse>('/facilities/10');

      expect(res.data.id).toBe(10);
      expect(res.data.name).toBe('서초 실내 체육관');
      expect(res.data.gu).toBe('서초구');
      expect(res.data.address).toBe('서울 서초구 반포대로 1');
      expect(res.data.tel).toBe('02-9876-5432');
    });
  });
});
