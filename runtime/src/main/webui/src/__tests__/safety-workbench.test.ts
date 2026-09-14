import { describe, it, expect } from "vitest";
import { safetyWorkbench, safetyWorkbenchDatasets } from "../views/safety-workbench.js";

describe("safety-workbench view", () => {
  it("returns a dockWorkbench component", () => {
    const component = safetyWorkbench("test-trial-id");
    expect(component).toBeDefined();
    expect((component as any).props?.__dockConfig).toBeDefined();
  });

  it("dockWorkbench has right and bottom zones", () => {
    const component = safetyWorkbench("test-trial-id");
    const config = (component as any).props.__dockConfig;
    expect(config.right).toHaveLength(2);
    expect(config.bottom).toHaveLength(1);
  });

  it("exports datasets array", () => {
    expect(safetyWorkbenchDatasets).toBeDefined();
  });
});
