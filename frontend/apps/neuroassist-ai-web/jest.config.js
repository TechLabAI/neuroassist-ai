/* eslint-disable */
// Espelha o jest.config.js dos libs (ver libs/acs/jest.config.js): mesma razao
// para o transform incluir .mjs (o Angular so publica ESM) e para o
// moduleNameMapper — o Jest roda em CJS e nao le os paths do tsconfig.base.
module.exports = {
  displayName: 'neuroassist-ai-web',
  preset: '../../jest.preset.js',
  setupFilesAfterEnv: ['<rootDir>/src/test-setup.ts'],
  testEnvironment: 'jsdom',
  transform: {
    '^.+\.(ts|mts|mjs|html)$': [
      'ts-jest',
      { tsconfig: '<rootDir>/tsconfig.spec.json' },
    ],
  },
  transformIgnorePatterns: ['/node_modules/(?!@angular)'],
  moduleNameMapper: {
    '^@neuroassist/acs$': '<rootDir>/../../libs/acs/src/index.ts',
    '^@neuroassist/dash$': '<rootDir>/../../libs/dash/src/index.ts',
    '^@neuroassist/edge$': '<rootDir>/../../libs/edge/src/index.ts',
    '^@neuroassist/evnt$': '<rootDir>/../../libs/evnt/src/index.ts',
    '^@neuroassist/gest$': '<rootDir>/../../libs/gest/src/index.ts',
    '^@neuroassist/hist$': '<rootDir>/../../libs/hist/src/index.ts',
    '^@neuroassist/pac$': '<rootDir>/../../libs/pac/src/index.ts',
    '^@neuroassist/shared/(.*)$': '<rootDir>/../../libs/shared/$1/src/index.ts',
  },
  coverageDirectory: '../../coverage/apps/neuroassist-ai-web',
};
