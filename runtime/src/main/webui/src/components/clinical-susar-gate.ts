export class ClinicalSusarGate extends HTMLElement {
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
        <button id="approve-gate-btn"
                style="background: #388e3c; color: white; padding: 12px 24px; border: none; border-radius: 4px; font-size: 14px; cursor: pointer; font-weight: 500;">
          Approve SUSAR Determination
        </button>
        <p id="resolution-status" style="margin-top: 10px; color: #666; font-size: 14px;">Loading...</p>
      </div>
    `;

    this.querySelector("#approve-gate-btn")!.addEventListener("click", () => this.approve(), { signal: this._abortController!.signal });
  }

  private async loadState() {
    const signal = this._abortController!.signal;
    const btn = this.querySelector("#approve-gate-btn") as HTMLButtonElement;
    const status = this.querySelector("#resolution-status") as HTMLParagraphElement;

    try {
      const res = await fetch(`/trials/${this.trialId}/adverse-events`, { signal });
      const data = await res.json();
      const requested = data.find((ae: Record<string, unknown>) => ae.escalationStatus === "REQUESTED");

      if (requested) {
        btn.dataset.aeId = requested.id;
        status.textContent = "SUSAR gate pending for AE " + requested.id;
        status.style.color = "#1976d2";
      } else {
        const completed = data.find((ae: Record<string, unknown>) => ae.escalationStatus === "COMPLETED");
        if (completed) {
          btn.disabled = true;
          btn.textContent = "SUSAR Gate Already Approved ✓";
          btn.style.background = "#757575";
          btn.style.cursor = "not-allowed";
          status.textContent = "Gate approval complete.";
          status.style.color = "#2e7d32";
        } else {
          btn.disabled = true;
          btn.textContent = "No AE to Approve";
          btn.style.background = "#757575";
          btn.style.cursor = "not-allowed";
          status.textContent = "Report a Grade 4 AE in Step 5 first.";
          status.style.color = "#c62828";
        }
      }
    } catch (err) {
      if (signal.aborted) return;
      status.textContent = "Error loading adverse events: " + (err instanceof Error ? err.message : String(err));
      status.style.color = "#c62828";
    }
  }

  private async approve() {
    const signal = this._abortController!.signal;
    const btn = this.querySelector("#approve-gate-btn") as HTMLButtonElement;
    const status = this.querySelector("#resolution-status") as HTMLParagraphElement;
    const aeId = btn.dataset.aeId;
    if (!aeId) return;

    btn.disabled = true;
    btn.textContent = "Approving...";
    status.textContent = "Completing gate WorkItem...";
    status.style.color = "#f57c00";

    try {
      const res = await fetch(`/demo/adverse-events/${aeId}/approve-susar-gate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal,
      });
      if (!res.ok) throw new Error("HTTP " + res.status);
      const result = await res.json();

      btn.textContent = "SUSAR Gate Approved ✓";
      btn.style.background = "#2e7d32";
      status.innerHTML = `
        <strong>Gate Decision:</strong> ${result.gateDecision || "APPROVED"}<br>
        <strong>Investigator ID:</strong> ${result.investigatorId || "demo-investigator"}<br>
        <strong>Attestation:</strong> ${result.attestation || "ENDORSED"} → safety-accuracy dimension<br>
        <strong>Trust Score Before:</strong> ${result.trustScoreBefore !== null && result.trustScoreBefore !== undefined ? result.trustScoreBefore.toFixed(3) : "N/A"}<br>
        <strong>Trust Score After:</strong> ${result.trustScoreAfter !== null && result.trustScoreAfter !== undefined ? result.trustScoreAfter.toFixed(3) : "N/A"}<br>
        <strong>Regulatory Submission:</strong> IND report created
      `;
      status.style.color = "#2e7d32";
      setTimeout(() => window.location.reload(), 1000);
    } catch (err) {
      if (signal.aborted) return;
      btn.disabled = false;
      btn.textContent = "Approve SUSAR Determination";
      btn.style.background = "#388e3c";
      status.textContent = "Error: " + (err instanceof Error ? err.message : String(err));
      status.style.color = "#c62828";
    }
  }
}
