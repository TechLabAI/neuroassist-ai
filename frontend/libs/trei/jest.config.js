/* eslint-disable */
module.exports = {
  displayName: 'trei',
  preset: '../../jest.preset.js',
  setupFilesAfterEnv: ['<rootDir>/src/test-setup.ts'],
  testEnvironment: 'jsdom',
  // Angular ships only as ESM (.mjs). The transform must include .mjs so
  // ts-jest can compile Angular packages to CommonJS for Jest's CJS runner.
  transform: {
    '^.+\\.(ts|mts|mjs|html)$': [
      'ts-jest',
      { tsconfig: '<rootDir>/tsconfig.spec.json' },
    ],
  },
  transformIgnorePatterns: ['/node_modules/(?!@angular)'],
  moduleNameMapper: {
    '^@neuroassist/trei$': '<rootDir>/src/index.ts',
    '^@neuroassist/shared/auth-session$': '<rootDir>/../shared/auth-session/src/index.ts',
    '^@neuroassist/shared/error-model$': '<rootDir>/../shared/error-model/src/index.ts',
    '^@neuroassist/shared/observability$': '<rootDir>/../shared/observability/src/index.ts',
    '^@neuroassist/shared/access-decision$': '<rootDir>/../shared/access-decision/src/index.ts',
  },
  coverageDirectory: '../../coverage/libs/trei',
};
