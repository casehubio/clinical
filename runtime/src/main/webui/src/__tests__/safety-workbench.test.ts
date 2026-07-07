import { describe, it, expect } from "vitest";
import { safetyWorkbench, safetyWorkbenchDatasets } from "../views/safety-workbench.js";

describe("safety-workbench view", () => {
  it("returns a defined component", () => {
    const component = safetyWorkbench();
    expect(component).toBeDefined();
  });

  it("exports datasets including adverse-events", () => {
    expect(safetyWorkbenchDatasets.length).toBeGreaterThan(0);
  });
});
