import { describe, it, expect, beforeAll } from "vitest";
import { ClinicalCommitmentLifecycle } from "../components/commitment-lifecycle.js";

describe("ClinicalCommitmentLifecycle", () => {
  beforeAll(() => {
    if (!customElements.get("commitment-lifecycle")) {
      customElements.define("commitment-lifecycle", ClinicalCommitmentLifecycle);
    }
  });

  it("is a valid custom element", () => {
    const el = document.createElement("commitment-lifecycle") as ClinicalCommitmentLifecycle;
    expect(el).toBeInstanceOf(HTMLElement);
  });

  it("has default stages matching qhorus lifecycle", () => {
    const el = document.createElement("commitment-lifecycle") as ClinicalCommitmentLifecycle;
    expect(el.stages).toHaveLength(4);
    expect(el.stages.map(s => s.key)).toEqual(["COMMANDED", "ACKNOWLEDGED", "DONE", "DECLINED"]);
  });

  it("renders empty state when no commitmentId", async () => {
    const el = document.createElement("commitment-lifecycle") as ClinicalCommitmentLifecycle;
    document.body.appendChild(el);
    await el.updateComplete;
    expect(el.shadowRoot?.textContent).toContain("No commitment selected");
    el.remove();
  });
});
