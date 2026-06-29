export class ClinicalPiApproval extends HTMLElement {
  private _initialized = false;
  private _abortController: AbortController | null = null;

  static get observedAttributes() {
    return ["trial-id"];
  }

  connectedCallback() {
    if (this._initialized) return;
    this._initialized = true;
    this._abortController = new AbortController();
    this.render();
    this.loadState();
  }

  disconnectedCallback() {
    this._abortController?.abort();
  }

  private get trialId(): string {
    return this.getAttribute("trial-id") ?? "";
  }

  private render() {
    this.innerHTML = `
      <div style="margin: 20px 0;">
        <button id="approve-pi-btn"
                style="background: #1976d2; color: white; padding: 12px 24px; border: none; border-radius: 4px; font-size: 14px; cursor: pointer; font-weight: 500;"
                disabled>
          Approve as PI
        </button>
        <p id="pi-approval-status" style="margin-top: 10px; color: #666; font-size: 14px;">Loading...</p>
      </div>
    `;

    this.querySelector("#approve-pi-btn")!.addEventListener("click", () => this.approve(), { signal: this._abortController!.signal });
  }

  private async loadState() {
    const signal = this._abortController!.signal;
    const btn = this.querySelector("#approve-pi-btn") as HTMLButtonElement;
    const status = this.querySelector("#pi-approval-status") as HTMLParagraphElement;

    try {
      const res = await fetch(`/trials/${this.trialId}/deviations`, { signal });
      const data = await res.json();
      const commanded = data.find((d: Record<string, unknown>) => d.piApprovalStatus === "COMMANDED");

      if (commanded) {
        btn.disabled = false;
        btn.dataset.deviationId = commanded.id;
        status.textContent = "Ready to approve deviation " + commanded.id;
        status.style.color = "#1976d2";
      } else {
        const escalated = data.find((d: Record<string, unknown>) => d.piApprovalStatus === "ESCALATED");
        if (escalated) {
          status.textContent = "Deviation " + escalated.id + " already ESCALATED to IRB";
          status.style.color = "#388e3c";
        } else {
          status.textContent = "No COMMANDED deviation found — report one in Step 3 first";
          status.style.color = "#f57c00";
        }
      }
    } catch (err) {
      if (signal.aborted) return;
      status.textContent = "Error loading deviations: " + (err instanceof Error ? err.message : String(err));
      status.style.color = "#c62828";
    }
  }

  private async approve() {
    const signal = this._abortController!.signal;
    const btn = this.querySelector("#approve-pi-btn") as HTMLButtonElement;
    const status = this.querySelector("#pi-approval-status") as HTMLParagraphElement;
    const deviationId = btn.dataset.deviationId;
    if (!deviationId) return;

    btn.disabled = true;
    btn.textContent = "Approving...";
    status.textContent = "Sending PI approval...";
    status.style.color = "#f57c00";

    try {
      const res = await fetch(`/demo/deviations/${deviationId}/approve-pi`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal,
      });
      if (!res.ok) throw new Error("HTTP " + res.status);

      btn.textContent = "PI Approval Sent ✓";
      btn.style.background = "#388e3c";
      status.textContent = "PI approved — deviation will transition COMMANDED → APPROVED → ESCALATED. Refreshing...";
      status.style.color = "#2e7d32";
      setTimeout(() => window.location.reload(), 3000);
    } catch (err) {
      if (signal.aborted) return;
      btn.disabled = false;
      btn.textContent = "Approve as PI";
      status.textContent = "Error: " + (err instanceof Error ? err.message : String(err));
      status.style.color = "#c62828";
    }
  }
}
