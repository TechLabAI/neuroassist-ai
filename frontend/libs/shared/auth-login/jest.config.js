/* eslint-disable */
module.exports = {
  displayName: 'shared-auth-login',
  preset: '../../../jest.preset.js',
  setupFilesAfterEnv: ['<rootDir>/src/test-setup.ts'],
  testEnvironment: 'jsdom',
  // Angular só publica ESM (.mjs); o transform precisa cobrir .mjs para o
  // ts-jest compilar os pacotes @angular para CommonJS (mesma configuração
  // de libs/acs — divergir daqui quebra o runner sem mensagem útil).
  transform: {
    '^.+\.(ts|mts|mjs|html)$': [
      'ts-jest',
      { tsconfig: '<rootDir>/tsconfig.spec.json' },
    ],
  },
  transformIgnorePatterns: ['/node_modules/(?!@angular)'],
  moduleNameMapper: {
    '^@neuroassist/shared/auth-session$': '<rootDir>/../auth-session/src/index.ts',
    '^@neuroassist/shared/access-http-base$': '<rootDir>/../access-http-base/src/index.ts',
  },
  coverageDirectory: '../../../coverage/libs/shared/auth-login',
};
