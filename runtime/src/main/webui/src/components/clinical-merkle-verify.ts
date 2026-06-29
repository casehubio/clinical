export class ClinicalMerkleVerify extends HTMLElement {
  private _initialized = false;
  private _abortController: AbortController | null = null;

  static get observedAttributes() {
    return ["trial-id", "site-id", "patient-id"];
  }

  connectedCallback() {
    if (this._initialized) return;
    this._initialized = true;
    this._abortController = new AbortController();
    this.render();
  }

  disconnectedCallback() {
    this._abortController?.abort();
  }

  private get trialId(): string {
    return this.getAttribute("trial-id") ?? "";
  }

  private get siteId(): string | null {
    return this.getAttribute("site-id");
  }

  private get patientId(): string | null {
    return this.getAttribute("patient-id");
  }

  private get verifyUrl(): string {
    if (this.siteId && this.patientId) {
      return `/trials/${this.trialId}/sites/${this.siteId}/patients/${this.patientId}/ledger/verify`;
    }
    return `/trials/${this.trialId}/ledger/verify`;
  }

  private get buttonLabel(): string {
    return this.siteId && this.patientId ? "Verify Ledger Integrity" : "Verify All Entries";
  }

  private render() {
    this.innerHTML = `
      <div style="margin: 20px 0; padding: 20px; border: 2px solid #1976d2; border-radius: 8px; background: #e3f2fd;">
        <h3 style="margin-top: 0; color: #1976d2;">Merkle Chain Verification</h3>
        <p style="font-size: 14px; line-height: 1.6;">
          Click the button below to verify the integrity of the ledger entries.
          The verification runs against the Merkle Mountain Range — a cryptographic accumulator that
          ensures no entry can be altered without detection.
        </p>
        <button id="verify-btn"
                style="background: #1976d2; color: white; padding: 12px 24px; border: none; border-radius: 4px; font-size: 14px; cursor: pointer; font-weight: 500; margin-top: 10px;">
          ${this.buttonLabel}
        </button>
        <div id="verify-result" style="margin-top: 15px; font-size: 14px; display: none;"></div>
      </div>
    `;

    this.querySelector("#verify-btn")!.addEventListener("click", () => this.verify(), { signal: this._abortController!.signal });
  }

  private async verify() {
    const signal = this._abortController!.signal;
    const btn = this.querySelector("#verify-btn") as HTMLButtonElement;
    const result = this.querySelector("#verify-result") as HTMLDivElement;

    btn.disabled = true;
    btn.textContent = "Verifying...";
    result.style.display = "block";
    result.innerHTML = '<p style="color: #666;">Running Merkle verification...</p>';

    try {
      const res = await fetch(this.verifyUrl, { signal });
      if (!res.ok) throw new Error("HTTP " + res.status);
      const data = await res.json();

      if (data.valid) {
        result.innerHTML = `
          <div style="padding: 15px; background: #e8f5e9; border-left: 4px solid #388e3c; border-radius: 4px;">
            <p style="margin: 0; color: #2e7d32; font-weight: 600; font-size: 16px;">
              ✓ VERIFIED
            </p>
            <p style="margin: 10px 0 0 0; color: #388e3c;">
              All ledger entries passed Merkle verification. The audit trail is cryptographically intact.
            </p>
            <p style="margin: 10px 0 0 0; color: #555; font-family: monospace; font-size: 12px;">
              <strong>Merkle Root:</strong><br>
              <code style="word-break: break-all;">${data.merkleRoot || "N/A"}</code>
            </p>
          </div>
        `;
      } else {
        result.innerHTML = `
          <div style="padding: 15px; background: #ffebee; border-left: 4px solid #c62828; border-radius: 4px;">
            <p style="margin: 0; color: #c62828; font-weight: 600; font-size: 16px;">
              ✗ VERIFICATION FAILED
            </p>
            <p style="margin: 10px 0 0 0; color: #d32f2f;">
              The ledger entries failed Merkle verification. This indicates tampering or data corruption.
            </p>
          </div>
        `;
      }
      btn.textContent = "Verify Again";
      btn.disabled = false;
    } catch (err) {
      if (signal.aborted) return;
      result.innerHTML = `
        <div style="padding: 15px; background: #fff3e0; border-left: 4px solid #f57c00; border-radius: 4px;">
          <p style="margin: 0; color: #e65100; font-weight: 600;">Error</p>
          <p style="margin: 10px 0 0 0; color: #f57c00;">
            ${err instanceof Error ? err.message : String(err)}
          </p>
        </div>
      `;
      btn.textContent = this.buttonLabel;
      btn.disabled = false;
    }
  }
}
