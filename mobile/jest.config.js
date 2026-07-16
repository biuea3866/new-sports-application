/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-expo',
  setupFiles: ['./jest.setup.ts'],
  testMatch: ['**/__tests__/**/*.test.(ts|tsx)', '**/*.test.(ts|tsx)'],
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json'],
  transformIgnorePatterns: [
    'node_modules/(?!((jest-)?react-native|@react-native(-community)?)|expo(nent)?|@expo(nent)?/.*|@expo-google-fonts/.*|react-navigation|@react-navigation/.*|@unimodules/.*|unimodules|sentry-expo|native-base|react-native-svg|zustand|react-native-mmkv|@tanstack)',
  ],
  moduleNameMapper: {
    '^@/api/(.*)$': '<rootDir>/api/$1',
    '^@/lib/(.*)$': '<rootDir>/lib/$1',
    '^@/app/(.*)$': '<rootDir>/app/$1',
    // FacilityMap.web.tsx가 import하는 leaflet CSS — jsdom 테스트 환경에는 스타일 로더가
    // 없으므로 빈 모듈로 치환한다 (leaflet 모듈 자체는 각 테스트가 jest.mock으로 대체).
    '\\.css$': '<rootDir>/jest.style-mock.js',
  },
  collectCoverageFrom: [
    'api/**/*.ts',
    'lib/**/*.ts',
    'components/**/*.tsx',
    '!**/__tests__/**',
    '!**/node_modules/**',
  ],
  coverageThreshold: {
    global: {
      lines: 80,
      branches: 70,
      functions: 80,
      statements: 80,
    },
  },
};
