import { describe, it, expect } from "vitest";
import { DEMO_MODE, TRIAL_ID, dualDataset } from "../datasets.js";

describe("dualDataset", () => {
  it("returns a dataset object", () => {
    const ds = dualDataset("test", "/api/test", "id,name\n1,Alpha\n2,Beta");
    expect(ds).toBeDefined();
    expect(ds.uuid).toBeDefined();
  });

  it("exports DEMO_MODE constant", () => {
    expect(typeof DEMO_MODE).toBe("boolean");
  });

  it("exports TRIAL_ID constant", () => {
    expect(TRIAL_ID).toBeDefined();
    expect(typeof TRIAL_ID).toBe("string");
  });

  it("TRIAL_ID matches demo UUID when DEMO_MODE is not explicitly set", () => {
    // In test environment without VITE_DEMO_MODE, it defaults to the demo UUID
    expect(TRIAL_ID).toBe("316e3846-4ea7-3b18-a6f7-e01ce6582a69");
  });
});
