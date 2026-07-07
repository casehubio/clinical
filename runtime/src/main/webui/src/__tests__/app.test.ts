import { describe, it, expect } from "vitest";

describe("app shell", () => {
  it("exports an app component", async () => {
    const { app } = await import("../app.js");
    expect(app).toBeDefined();
  });
});

describe("view functions", () => {
  it("exports workQueue", async () => {
    const { workQueue } = await import("../views/work-queue.js");
    expect(workQueue).toBeDefined();
    expect(typeof workQueue).toBe("function");
  });

  it("exports safetyWorkbench", async () => {
    const { safetyWorkbench } = await import("../views/safety-workbench.js");
    expect(safetyWorkbench).toBeDefined();
    expect(typeof safetyWorkbench).toBe("function");
  });

  it("exports protocolWorkbench", async () => {
    const { protocolWorkbench } = await import("../views/protocol-workbench.js");
    expect(protocolWorkbench).toBeDefined();
    expect(typeof protocolWorkbench).toBe("function");
  });

  it("exports operations", async () => {
    const { operations } = await import("../views/operations.js");
    expect(operations).toBeDefined();
    expect(typeof operations).toBe("function");
  });
});
