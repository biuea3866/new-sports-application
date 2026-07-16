const path = require('path');
const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

/**
 * 웹 번들에서 zustand를 CommonJS 빌드로 강제 해석합니다.
 *
 * Expo 웹 번들은 classic script(<script src>)로 로드되므로 `import.meta`가
 * 들어 있으면 파싱 단계에서 SyntaxError가 나고 앱 전체가 백지가 됩니다.
 * zustand는 exports 맵에 `browser` 조건이 없어서 웹에서는 `react-native`(CJS)가
 * 아니라 `import`(ESM) 조건으로 해석되는데, ESM 빌드의 devtools 미들웨어가
 * `import.meta.env`를 참조합니다. CJS 빌드에는 이 코드가 없습니다.
 *
 * 네이티브(ios/android)는 exports의 `react-native` 조건이 이미 CJS를 가리키므로
 * 웹에서만 개입합니다.
 */
const zustandRoot = path.dirname(require.resolve('zustand/package.json'));
const defaultResolveRequest = config.resolver.resolveRequest;

config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (platform === 'web' && (moduleName === 'zustand' || moduleName.startsWith('zustand/'))) {
    const subpath = moduleName === 'zustand' ? 'index' : moduleName.slice('zustand/'.length);
    return {
      type: 'sourceFile',
      filePath: path.join(zustandRoot, `${subpath}.js`),
    };
  }
  return (defaultResolveRequest ?? context.resolveRequest)(context, moduleName, platform);
};

module.exports = config;
