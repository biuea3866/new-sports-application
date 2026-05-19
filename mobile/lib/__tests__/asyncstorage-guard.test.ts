/**
 * R-02: AsyncStorage에 토큰이 절대 기록되지 않음을 런타임 가드로 확인한다
 */
import { guardedAsyncStorage } from '../asyncstorage-guard';
import { execSync } from 'child_process';

jest.mock('expo-router', () => ({
  router: { replace: jest.fn() },
}));

describe('AsyncStorage 토큰 저장 가드', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('런타임 가드 — setItem', () => {
    const tokenKeys = [
      'accessToken',
      'refreshToken',
      'token',
      'auth_token',
      'jwt',
      'bearer',
      'userToken',
    ];

    tokenKeys.forEach((key) => {
      it(`key="${key}" 는 setItem 호출 시 에러를 던진다`, async () => {
        await expect(guardedAsyncStorage.setItem(key, 'some-value')).rejects.toThrow(
          'AsyncStorage에 토큰 저장 금지'
        );
      });
    });

    it('토큰 관련 키가 아닌 경우 setItem이 정상 동작한다', async () => {
      await expect(
        guardedAsyncStorage.setItem('user-preferences', 'data')
      ).resolves.toBeUndefined();

      await expect(
        guardedAsyncStorage.setItem('app-settings', 'settings-data')
      ).resolves.toBeUndefined();
    });
  });

  describe('런타임 가드 — getItem', () => {
    it('getItem은 토큰 관련 키 접근 시 경고를 출력하고 null을 반환한다', async () => {
      const consoleWarnSpy = jest.spyOn(console, 'warn').mockImplementation();

      const result = await guardedAsyncStorage.getItem('accessToken');

      expect(result).toBeNull();
      expect(consoleWarnSpy).toHaveBeenCalledWith(
        expect.stringContaining('AsyncStorage에서 토큰 읽기 시도 차단')
      );

      consoleWarnSpy.mockRestore();
    });

    it('일반 키는 정상적으로 null을 반환한다 (저장된 값 없음)', async () => {
      const consoleWarnSpy = jest.spyOn(console, 'warn').mockImplementation();

      const result = await guardedAsyncStorage.getItem('user-preferences');

      expect(result).toBeNull();
      expect(consoleWarnSpy).not.toHaveBeenCalled();

      consoleWarnSpy.mockRestore();
    });
  });

  describe('런타임 가드 — removeItem', () => {
    it('removeItem은 토큰 관련 키 접근 시 경고를 출력한다', async () => {
      const consoleWarnSpy = jest.spyOn(console, 'warn').mockImplementation();

      await guardedAsyncStorage.removeItem('refreshToken');

      expect(consoleWarnSpy).toHaveBeenCalledWith(
        expect.stringContaining('AsyncStorage에서 토큰 삭제 시도 차단')
      );

      consoleWarnSpy.mockRestore();
    });

    it('removeItem은 일반 키에 대해 정상 동작한다', async () => {
      const consoleWarnSpy = jest.spyOn(console, 'warn').mockImplementation();

      await expect(guardedAsyncStorage.removeItem('user-preferences')).resolves.toBeUndefined();

      expect(consoleWarnSpy).not.toHaveBeenCalled();

      consoleWarnSpy.mockRestore();
    });
  });

  describe('정적 분석 (grep)', () => {
    it('소스 코드 lib/ api/ app/ 에 AsyncStorage 직접 import가 없다', () => {
      const result = execSync(
        `grep -rn "@react-native-async-storage/async-storage" ` +
          `/Users/biuea/sports-application/mobile/lib ` +
          `/Users/biuea/sports-application/mobile/api ` +
          `/Users/biuea/sports-application/mobile/app ` +
          `--include="*.ts" --include="*.tsx" ` +
          `--exclude-dir="__tests__" 2>/dev/null || echo "NONE"`,
        { encoding: 'utf-8' }
      ).trim();

      expect(result).toBe('NONE');
    });
  });
});
