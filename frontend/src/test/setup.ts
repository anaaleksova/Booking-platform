import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach } from 'vitest';

// findBy…/waitFor wait 1 s by default. With every test file running in
// parallel a page can take longer than that to appear, failing at random.
configure({ asyncUtilTimeout: 3000 });

afterEach(() => {
  cleanup();
});
