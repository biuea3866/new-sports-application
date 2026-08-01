/**
 * 좌석 라벨 포맷 — 구역명이 중복 노출되지 않는지 검증한다.
 *
 * 회귀 배경: 좌석 버튼이 전부 "A석-1-A석-01" 로 렌더돼 구역명이 두 번 나왔다
 * (유즈케이스 캡쳐 17-이벤트-좌석-선택). 좌석번호(seatNo)에 이미 구역 접두사가 포함된
 * 데이터가 들어오는데 화면이 구역명을 다시 앞에 붙인 것이 원인이다.
 */
import { formatSeatDescription, formatSeatLabel } from '../seat-format';

describe('formatSeatLabel', () => {
  it('구역·열·좌석번호를 하이픈으로 잇는다', () => {
    expect(formatSeatLabel('A석', '1', '01')).toBe('A석-1-01');
  });

  it('좌석번호에 구역 접두사가 이미 있으면 중복해서 붙이지 않는다', () => {
    expect(formatSeatLabel('A석', '1', 'A석-01')).toBe('A석-1-01');
  });

  it('구역 접두사가 구분자 없이 붙어 있어도 중복을 제거한다', () => {
    expect(formatSeatLabel('R석', '2', 'R석05')).toBe('R석-2-05');
  });

  it('구역명과 무관한 좌석번호는 그대로 유지한다', () => {
    expect(formatSeatLabel('S석', '3', 'B12')).toBe('S석-3-B12');
  });

  it('좌석번호가 구역명과 완전히 같으면 좌석번호를 비워 중복을 없앤다', () => {
    expect(formatSeatLabel('A석', '1', 'A석')).toBe('A석-1');
  });

  it('열 정보가 없으면 구역과 좌석번호만 잇는다', () => {
    expect(formatSeatLabel('A석', '', 'A석-07')).toBe('A석-07');
  });
});

describe('formatSeatDescription', () => {
  it('사람이 읽는 서술형으로 표기한다', () => {
    expect(formatSeatDescription('A석', '1', '01')).toBe('A석구역 1열 01번');
  });

  it('좌석번호의 구역 접두사를 중복 노출하지 않는다', () => {
    expect(formatSeatDescription('A석', '1', 'A석-01')).toBe('A석구역 1열 01번');
  });
});
