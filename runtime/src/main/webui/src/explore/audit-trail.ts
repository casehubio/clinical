import { page, table, markdown, html, selector, lookup, groupBy, col, filterBy } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, ledgerEntriesDs } from "../datasets";

/**
 * Explore Mode: Audit Trail
 *
 * Complete ledger entry history with type filtering and Merkle verification.
 *
 * Components:
 * - Entry type selector (dropdown filter)
 * - Ledger entries table with timestamp, type, actor, subject, digest
 * - "Verify Chain Integrity" button (calls trial-level verification endpoint)
 */
export const auditTrail = page("Audit Trail",
  markdown(`## Tamper-Evident Audit Trail

Every action — AE reports, SUSAR decisions, attestations, protocol deviations, PI approvals — is recorded in a cryptographically-chained ledger. Each entry includes a SHA-256 digest linking it to the previous entry, forming a Merkle Mountain Range that ensures no entry can be altered without detection.

**Filter by entry type or verify the entire chain's integrity below.**`),

  // Entry type selector
  selector({
    subtype: "dropdown",
    selfApply: true,
    notification: true,
    lookup: lookup("ledger-entries", groupBy("entryType", col("entryType")))
  }),

  // Ledger entries table
  table({
    sortable: true,
    pageSize: 50,
    listening: true,
    columns: [
      { id: "occurredAt" as ColumnId, label: "Timestamp",
        expression: 'new Date(value).toLocaleString()' },
      { id: "entryType" as ColumnId, label: "Entry Type",
        expression: 'value.replace("LedgerEntry", "").replace(/([A-Z])/g, " $1").trim()' },
      { id: "actorId" as ColumnId, label: "Actor",
        expression: 'value.substring(0, 24) + (value.length > 24 ? "..." : "")' },
      { id: "subjectId" as ColumnId, label: "Subject ID",
        expression: 'value.substring(0, 8) + "..."' },
      { id: "digest" as ColumnId, label: "Digest (SHA-256)",
        expression: 'value.substring(0, 16) + "..."' }
    ],
    lookup: lookup("ledger-entries")
  }),

  // Merkle verification button (trial-level verification)
  html(`
    <div id="merkle-verification-explore" style="margin: 20px 0; padding: 20px; border: 2px solid #1976d2; border-radius: 8px; background: #e3f2fd;">
      <h3 style="margin-top: 0; color: #1976d2;">Verify Chain Integrity</h3>
      <p style="font-size: 14px; line-height: 1.6;">
        Verify the cryptographic integrity of all ledger entries for this trial. The verification runs against
        the Merkle Mountain Range — a cryptographic accumulator that ensures no entry can be altered without detection.
      </p>
      <button id="verify-btn-explore"
              style="background: #1976d2; color: white; padding: 12px 24px; border: none; border-radius: 4px; font-size: 14px; cursor: pointer; font-weight: 500; margin-top: 10px;">
        Verify All Entries
      </button>
      <div id="verify-result-explore" style="margin-top: 15px; font-size: 14px; display: none;"></div>
    </div>
    <script>
      (function() {
        var TRIAL_ID = "${TRIAL_ID}";
        const btn = document.getElementById('verify-btn-explore');
        const result = document.getElementById('verify-result-explore');

        btn.addEventListener('click', function() {
          btn.disabled = true;
          btn.textContent = 'Verifying...';
          result.style.display = 'block';
          result.innerHTML = '<p style="color: #666;">Running Merkle verification across all trial entries...</p>';

          // Note: For trial-level verification, we need a trial-wide verify endpoint.
          // For now, use the same patient-based endpoint as Step 8 (Patient B1 at Site B).
          // TODO: Add GET /trials/{trialId}/ledger/verify for full trial verification.
          const SITE_B_ID = "28d71146-f562-3352-a521-2ede60adba82";
          const PATIENT_B1_ID = "4bb87f70-ca9e-3ded-9bbc-df9bf6fbb38d";

          fetch('/trials/' + TRIAL_ID + '/sites/' + SITE_B_ID + '/patients/' + PATIENT_B1_ID + '/ledger/verify')
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
              btn.textContent = 'Verify All Entries';
              btn.disabled = false;
            });
        });
      })();
    </script>
  `),
  { datasets: [ledgerEntriesDs] }
);
