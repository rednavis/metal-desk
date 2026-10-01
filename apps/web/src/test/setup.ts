import "@testing-library/jest-dom/vitest";
import { cleanup, configure } from "@testing-library/react";
import { afterEach } from "vitest";

// A `findBy*` or `waitFor` gives up after one second by default. A two-core CI runner needs more than that for a
// multi-request screen change (the checkout steps), so a test there failed on CI and passed on a laptop. The
// ceiling only costs time when something really is broken: a passing wait returns as soon as it is satisfied.
configure({ asyncUtilTimeout: 5000 });

afterEach(() => {
  cleanup();
});
