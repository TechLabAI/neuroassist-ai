/* eslint-disable */
module.exports = {
  displayName: 'shared-access-http-base',
  preset: '../../../jest.preset.js',
  setupFilesAfterEnv: ['<rootDir>/src/test-setup.ts'],
  testEnvironment: 'jsdom',
  transform: {
    '^.+\\.(ts|mts|mjs|html)$': [
      'ts-jest',
      { tsconfig: '<rootDir>/tsconfig.spec.json' },
    ],
  },
  transformIgnorePatterns: ['/node_modules/(?!@angular)'],
  moduleNameMapper: {
    '^@neuroassist/shared/access-http-base$': '<rootDir>/src/index.ts',
    '^@neuroassist/shared/auth-session$': '<rootDir>/../auth-session/src/index.ts',
    '^@neuroassist/shared/empresa-context$': '<rootDir>/../empresa-context/src/index.ts',
    '^@neuroassist/shared/access-decision$': '<rootDir>/../access-decision/src/index.ts',
    '^@neuroassist/shared/error-model$': '<rootDir>/../error-model/src/index.ts',
    '^@neuroassist/shared/observability$': '<rootDir>/../observability/src/index.ts',
    '^@neuroassist/shared/contracts$': '<rootDir>/../contracts/src/index.ts',
    '^@neuroassist/shared/ui-tokens$': '<rootDir>/../ui-tokens/src/index.ts',
    '^@neuroassist/shared/ui-components$': '<rootDir>/../ui-components/src/index.ts',
    '^@neuroassist/shared/ui-feedback$': '<rootDir>/../ui-feedback/src/index.ts',
  },
  coverageDirectory: '../../coverage/libs/shared/access-http-base',
};
