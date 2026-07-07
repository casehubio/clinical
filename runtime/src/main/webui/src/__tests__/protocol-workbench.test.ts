import { describe, it, expect } from "vitest";
import { protocolWorkbench, protocolWorkbenchDatasets } from "../views/protocol-workbench.js";

describe("protocol-workbench view", () => {
  it("returns a defined component", () => {
    expect(protocolWorkbench()).toBeDefined();
  });

  it("exports datasets including deviations", () => {
    expect(protocolWorkbenchDatasets.length).toBeGreaterThan(0);
  });
});
