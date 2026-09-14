import { describe, it, expect } from "vitest";
import { operations, operationsDatasets } from "../views/operations.js";

describe("operations view", () => {
  it("returns a dockWorkbench component", () => {
    const component = operations();
    expect(component).toBeDefined();
    expect((component as any).props?.__dockConfig).toBeDefined();
  });

  it("dockWorkbench has right and bottom zones", () => {
    const component = operations();
    const config = (component as any).props.__dockConfig;
    expect(config.right).toHaveLength(2);
    expect(config.bottom).toHaveLength(3);
  });

  it("exports datasets array", () => {
    expect(operationsDatasets).toBeDefined();
  });
});
