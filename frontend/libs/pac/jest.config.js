/* eslint-disable */
module.exports = {
  displayName: 'pac',
  preset: '../../jest.preset.js',
  setupFilesAfterEnv: ['<rootDir>/src/test-setup.ts'],
  testEnvironment: 'jsdom',
  // Angular 21 ships only as ESM (.mjs). The transform must include .mjs so
  // ts-jest can compile Angular packages to CommonJS for Jest's CJS runner.
  // transformIgnorePatterns overrides the default (/node_modules/) to allow
  // transforming @angular/* packages.
  transform: {
    '^.+\\.(ts|mts|mjs|html)$': [
      'ts-jest',
      { tsconfig: '<rootDir>/tsconfig.spec.json' },
    ],
  },
  transformIgnorePatterns: ['/node_modules/(?!@angular)'],
  moduleNameMapper: {
    '^@neuroassist/pac$': '<rootDir>/src/index.ts',
    '^@neuroassist/shared/contracts$': '<rootDir>/../shared/contracts/src/index.ts',
    '^@neuroassist/shared/access-http-base$': '<rootDir>/../shared/access-http-base/src/index.ts',
    '^@neuroassist/shared/auth-session$': '<rootDir>/../shared/auth-session/src/index.ts',
    '^@neuroassist/shared/empresa-context$': '<rootDir>/../shared/empresa-context/src/index.ts',
    '^@neuroassist/shared/access-decision$': '<rootDir>/../shared/access-decision/src/index.ts',
    '^@neuroassist/shared/error-model$': '<rootDir>/../shared/error-model/src/index.ts',
    '^@neuroassist/shared/observability$': '<rootDir>/../shared/observability/src/index.ts',
    '^@neuroassist/shared/ui-tokens$': '<rootDir>/../shared/ui-tokens/src/index.ts',
    '^@neuroassist/shared/ui-components$': '<rootDir>/../shared/ui-components/src/index.ts',
    '^@neuroassist/shared/ui-feedback$': '<rootDir>/../shared/ui-feedback/src/index.ts',
  },
  coverageDirectory: '../../coverage/libs/pac',
};
