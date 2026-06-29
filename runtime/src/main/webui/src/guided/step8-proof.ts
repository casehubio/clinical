import { page, table, markdown, html, lookup } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, ledgerEntriesDs } from "../datasets";
import { STEP8_NARRATIVE } from "../narrative";

// Site B and Patient B1 IDs from DemoDataSeeder — deterministic UUIDs
const SITE_B_ID = "28d71146-f562-3352-a521-2ede60adba82";
const PATIENT_B1_ID = "4bb87f70-ca9e-3ded-9bbc-df9bf6fbb38d";

/**
 * Step 8: The Proof
 *
 * Merkle verification showcase — the climax of the demo.
 *
 * Components:
 * - Narrative markdown
 * - Ledger entries table (timestamp, type, actor, summary)
 * - Merkle verification button via html() fetch
 * - Displays VERIFIED ✓ or FAILED ✗ with Merkle root hash
 *
 * The verification endpoint is GET /api/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/ledger/verify.
 * Returns: { valid: boolean, merkleRoot: string | null }
 *
 * This endpoint exists in PatientResource and works for both pre-seeded and live-action entries because
 * the seeder uses real service calls that produce genuine Merkle chains.
 */
export const step8Proof = page("8. The Proof",
  markdown(`## Tamper-Evident Audit Trail\n\n${STEP8_NARRATIVE}`),

  // Ledger entries table
  table({
    sortable: true,
    columns: [
      { id: "timestamp" as ColumnId, label: "Timestamp",
        expression: 'new Date(value).toLocaleString()' },
      { id: "entryType" as ColumnId, label: "Entry Type",
        expression: 'value.replace("LedgerEntry", "")' },
      { id: "actorId" as ColumnId, label: "Actor",
        expression: 'value.substring(0, 20) + (value.length > 20 ? "..." : "")' },
      { id: "subjectId" as ColumnId, label: "Subject ID",
        expression: 'value.substring(0, 8) + "..."' },
      { id: "digest" as ColumnId, label: "Digest (SHA-256)",
        expression: 'value.substring(0, 16) + "..."' }
    ],
    lookup: lookup(ledgerEntriesDs, [], [])
  }),

  // Merkle verification button and result display
  html(`
    <div id="merkle-verification" style="margin: 20px 0; padding: 20px; border: 2px solid #1976d2; border-radius: 8px; background: #e3f2fd;">
      <h3 style="margin-top: 0; color: #1976d2;">Merkle Chain Verification</h3>
      <p style="font-size: 14px; line-height: 1.6;">
        Click the button below to verify the integrity of the ledger entries for Patient B1 at Site B.
        The verification runs against the Merkle Mountain Range — a cryptographic accumulator that
        ensures no entry can be altered without detection.
      </p>
      <button id="verify-btn"
              style="background: #1976d2; color: white; padding: 12px 24px; border: none; border-radius: 4px; font-size: 14px; cursor: pointer; font-weight: 500; margin-top: 10px;">
        Verify Ledger Integrity
      </button>
      <div id="verify-result" style="margin-top: 15px; font-size: 14px; display: none;"></div>
    </div>
    <script>
      (function() {
        const btn = document.getElementById('verify-btn');
        const result = document.getElementById('verify-result');

        btn.addEventListener('click', function() {
          btn.disabled = true;
          btn.textContent = 'Verifying...';
          result.style.display = 'block';
          result.innerHTML = '<p style="color: #666;">Running Merkle verification...</p>';

          fetch('/api/trials/${TRIAL_ID}/sites/${SITE_B_ID}/patients/${PATIENT_B1_ID}/ledger/verify')
            .then(r => {
              if (!r.ok) throw new Error('HTTP ' + r.status);
              return r.json();
            })
            .then(data => {
              if (data.valid) {
                result.innerHTML = \`
                  <div style="padding: 15px; background: #e8f5e9; border-left: 4px solid #388e3c; border-radius: 4px;">
                    <p style="margin: 0; color: #2e7d32; font-weight: 600; font-size: 16px;">
                      ✓ VERIFIED
                    </p>
                    <p style="margin: 10px 0 0 0; color: #388e3c;">
                      All ledger entries passed Merkle verification. The audit trail is cryptographically intact.
                    </p>
                    <p style="margin: 10px 0 0 0; color: #555; font-family: monospace; font-size: 12px;">
                      <strong>Merkle Root:</strong><br>
                      <code style="word-break: break-all;">\${data.merkleRoot || 'N/A'}</code>
                    </p>
                  </div>
                \`;
              } else {
                result.innerHTML = \`
                  <div style="padding: 15px; background: #ffebee; border-left: 4px solid #c62828; border-radius: 4px;">
                    <p style="margin: 0; color: #c62828; font-weight: 600; font-size: 16px;">
                      ✗ VERIFICATION FAILED
                    </p>
                    <p style="margin: 10px 0 0 0; color: #d32f2f;">
                      The ledger entries failed Merkle verification. This indicates tampering or data corruption.
                    </p>
                  </div>
                \`;
              }
              btn.textContent = 'Verify Again';
              btn.disabled = false;
            })
            .catch(err => {
              result.innerHTML = \`
                <div style="padding: 15px; background: #fff3e0; border-left: 4px solid #f57c00; border-radius: 4px;">
                  <p style="margin: 0; color: #e65100; font-weight: 600;">
                    Error
                  </p>
                  <p style="margin: 10px 0 0 0; color: #f57c00;">
                    \${err.message}
                  </p>
                </div>
              \`;
              btn.textContent = 'Verify Ledger Integrity';
              btn.disabled = false;
            });
        });
      })();
    </script>
  `),

  // Explanation: what this means
  markdown(`### Why This Matters

Every ledger entry — AE reports, SUSAR decisions, attestations, protocol deviations, PI approvals — is hashed and chained into a Merkle Mountain Range. The digest includes:

- **Entry content** — all domain fields serialized to bytes
- **Actor ID** — who performed the action
- **Timestamp** — when it occurred
- **Previous entry digest** — cryptographic link to the prior entry

**Independent verification:** The Merkle root can be published to an external system (blockchain, timestamping authority, regulatory archive). An auditor can verify the entire chain without trusting CaseHub's database — the cryptographic proof stands alone.

**EU AI Act Art.12 compliance:** Every AI-agent decision has a \`ComplianceSupplement\` attached, recording:
- Input data used
- Output decision produced
- Risk classification
- Human oversight applied
- Trust score at decision time

This is Layer 8 in action: **tamper-evident audit with regulatory compliance supplements.**`),

  // Final callout
  markdown(`### The Demo Is Complete

You've seen:

1. **Accountability (Layers 1-4):** PI COMMAND and Commitment lifecycle — qhorus
2. **AI Governance (Layers 5-7):** Trust routing, oversight gates, attestation feedback — engine + ledger
3. **The Proof (Layer 8):** Merkle verification, compliance supplements — regulatory foundation

CaseHub provides what no LLM pipeline can: **structurally guaranteed compliance** through formal commitments, trust-weighted routing, unconditional oversight gates, and cryptographic audit trails.

This is the AI Fusion demo — accountability meets AI governance.`)
);
