import { describe, it, expect } from "vitest";
import { operations, operationsDatasets } from "../views/operations.js";

describe("operations view", () => {
  it("returns a defined component", () => {
    expect(operations()).toBeDefined();
  });

  it("exports datasets including trial-summary and agents", () => {
    expect(operationsDatasets.length).toBeGreaterThanOrEqual(4);
  });
});
