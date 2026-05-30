/**
 * U-01: GET /facilities 성공 시 시설 목록 페이지를 반환한다
 * U-02: GET /facilities/{id} 성공 시 시설 상세를 반환한다
 * U-03: 존재하지 않는 시설 id 호출 시 404 에러가 발생한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import type { FacilityPageResponse, FacilityResponse } from '../types';

describe('Facility API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  afterEach(() => mock.reset());

  const mockFacility: FacilityResponse = {
    id: 1,
    name: '강남 테니스장',
    gu: '강남구',
    type: 'OUTDOOR',
    address: '서울 강남구 테헤란로 100',
    parking: true,
    phone: '02-1234-5678',
  };

  const mockPage: FacilityPageResponse = {
    content: [mockFacility],
    number: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
    last: true,
  };

  describe('U-01: searchFacilities', () => {
    it('GET /facilities?gu=강남구 호출 시 시설 목록 페이지를 반환한다', async () => {
      mock.onGet('/facilities').reply(200, mockPage);

      const res = await client.get<FacilityPageResponse>('/facilities', {
        params: { gu: '강남구', page: 0, size: 50 },
      });

      expect(res.data.content).toHaveLength(1);
      expect(res.data.content[0].name).toBe('강남 테니스장');
      expect(res.data.content[0].gu).toBe('강남구');
      expect(res.data.totalElements).toBe(1);
    });

    it('빈 결과 시 content 배열이 비어있다', async () => {
      const emptyPage: FacilityPageResponse = {
        ...mockPage,
        content: [],
        totalElements: 0,
      };
      mock.onGet('/facilities').reply(200, emptyPage);

      const res = await client.get<FacilityPageResponse>('/facilities');
      expect(res.data.content).toHaveLength(0);
    });
  });

  describe('U-02: getFacility', () => {
    it('GET /facilities/1 호출 시 시설 상세를 반환한다', async () => {
      mock.onGet('/facilities/1').reply(200, mockFacility);

      const res = await client.get<FacilityResponse>('/facilities/1');

      expect(res.data.id).toBe(1);
      expect(res.data.type).toBe('OUTDOOR');
      expect(res.data.parking).toBe(true);
    });
  });

  describe('U-03: 없는 시설 조회', () => {
    it('GET /facilities/999 호출 시 404 에러가 발생한다', async () => {
      mock.onGet('/facilities/999').reply(404, { message: 'Facility not found' });

      await expect(client.get('/facilities/999')).rejects.toThrow();
    });
  });
});
