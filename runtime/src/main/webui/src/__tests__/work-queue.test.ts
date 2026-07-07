import { describe, it, expect } from "vitest";
import { workQueue, workQueueDatasets } from "../views/work-queue.js";

describe("work-queue view", () => {
  it("returns a defined component", () => {
    const component = workQueue();
    expect(component).toBeDefined();
  });

  it("exports datasets array", () => {
    expect(workQueueDatasets).toBeInstanceOf(Array);
  });
});
