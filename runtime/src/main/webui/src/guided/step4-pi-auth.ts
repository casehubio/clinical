import { page, columns, metric, table, markdown, lookup, groupBy, filterBy, col, html } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { deviationsDs, ledgerEntriesDs } from "../datasets";
import { STEP4_NARRATIVE } from "../narrative";

/**
 * Step 4: PI Authorisation & Commitment
 *
 * Components:
 * - Narrative markdown
 * - Action button: "Approve as PI" → POST to demo endpoint
 * - Commitment lifecycle display: COMMANDED → APPROVED → ESCALATED
 * - Deviation Merkle chain: COMMAND entry → resolution entry → IRB entry
 *
 * Action button uses html() with inline JavaScript fetch() to call /demo/deviations/{id}/approve-pi.
 * Button is enabled only if a COMMANDED deviation exists.
 *
 * After approval, the qhorus MessageReceivedEvent fires → PiResponseListener updates piApprovalStatus
 * to APPROVED, then (for CRITICAL deviations) ESCALATED.
 */
export const step4PiAuth = page("4. PI Authorisation",
  markdown(`## PI Authorisation & Commitment Lifecycle\n\n${STEP4_NARRATIVE}`),

  // Action button: Approve as PI
  html(`
    <div id="pi-approval-action" style="margin: 20px 0;">
      <button id="approve-pi-btn"
              style="background: #1976d2; color: white; padding: 12px 24px; border: none; border-radius: 4px; font-size: 14px; cursor: pointer; font-weight: 500;"
              disabled>
        Approve as PI
      </button>
      <p id="pi-approval-status" style="margin-top: 10px; color: #666; font-size: 14px;">Loading...</p>
    </div>
    <script>
      (function() {
        const btn = document.getElementById('approve-pi-btn');
        const status = document.getElementById('pi-approval-status');
        let commandedDeviationId = null;

        // Find COMMANDED deviation
        fetch('/api/trials/316e3846-4ea7-3b18-a6f7-e01ce6582a69/deviations')
          .then(r => r.json())
          .then(data => {
            const commanded = data.find(d => d.piApprovalStatus === 'COMMANDED');
            if (commanded) {
              commandedDeviationId = commanded.id;
              btn.disabled = false;
              status.textContent = 'Ready to approve deviation ' + commanded.id;
              status.style.color = '#1976d2';
            } else {
              const escalated = data.find(d => d.piApprovalStatus === 'ESCALATED');
              if (escalated) {
                status.textContent = 'Deviation ' + escalated.id + ' already ESCALATED to IRB';
                status.style.color = '#388e3c';
              } else {
                status.textContent = 'No COMMANDED deviation found — report one in Step 3 first';
                status.style.color = '#f57c00';
              }
            }
          })
          .catch(err => {
            status.textContent = 'Error loading deviations: ' + err.message;
            status.style.color = '#c62828';
          });

        btn.addEventListener('click', function() {
          if (!commandedDeviationId) return;

          btn.disabled = true;
          btn.textContent = 'Approving...';
          status.textContent = 'Sending PI approval...';
          status.style.color = '#f57c00';

          fetch('/api/demo/deviations/' + commandedDeviationId + '/approve-pi', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'}
          })
          .then(r => {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
          })
          .then(result => {
            btn.textContent = 'PI Approval Sent ✓';
            btn.style.background = '#388e3c';
            status.textContent = 'PI approved — MessageReceivedEvent fired. The deviation will transition COMMANDED → APPROVED → ESCALATED. Refresh page to see updated status.';
            status.style.color = '#2e7d32';

            // Auto-refresh after 3s to show state change
            setTimeout(() => {
              window.location.reload();
            }, 3000);
          })
          .catch(err => {
            btn.disabled = false;
            btn.textContent = 'Approve as PI';
            status.textContent = 'Error: ' + err.message;
            status.style.color = '#c62828';
          });
        });
      })();
    </script>
  `),

  // Deviations table with lifecycle status
  table({
    sortable: true,
    columns: [
      { id: "deviationType" as ColumnId, label: "Type" },
      { id: "severity" as ColumnId, label: "Severity" },
      { id: "piApprovalStatus" as ColumnId, label: "Commitment Lifecycle",
        expression: 'value === "COMMANDED" ? "1️⃣ COMMANDED" : value === "APPROVED" ? "2️⃣ APPROVED" : value === "ESCALATED" ? "3️⃣ ESCALATED" : value' },
      { id: "commandedAt" as ColumnId, label: "Commanded At",
        expression: 'value != null ? new Date(value).toLocaleString() : "—"' },
      { id: "respondedAt" as ColumnId, label: "Responded At",
        expression: 'value != null ? new Date(value).toLocaleString() : "—"' },
      { id: "escalatedAt" as ColumnId, label: "Escalated At",
        expression: 'value != null ? new Date(value).toLocaleString() : "—"' }
    ],
    lookup: lookup(deviationsDs, [], [])
  }),

  // Merkle chain: deviation-related ledger entries
  markdown(`### Tamper-Evident Audit Trail

Every step is recorded in the Merkle ledger — COMMAND sent, PI response received, IRB escalation triggered. Each entry is independently verifiable.`),

  table({
    sortable: true,
    pageSize: 10,
    columns: [
      { id: "timestamp" as ColumnId, label: "Timestamp",
        expression: 'new Date(value).toLocaleString()' },
      { id: "eventType" as ColumnId, label: "Event Type" },
      { id: "actorId" as ColumnId, label: "Actor" },
      { id: "sequenceNumber" as ColumnId, label: "Seq #" }
    ],
    lookup: lookup(
      ledgerEntriesDs,
      [filterBy("eventType", "CONTAINS", "DEVIATION")],
      []
    )
  }),

  // Commitment lifecycle explanation
  columns(
    { span: 6 },
    markdown(`### Commitment Lifecycle Stages

1️⃣ **COMMANDED** — Platform sends formal COMMAND to PI with 24h deadline
2️⃣ **APPROVED** — PI responds, Commitment closed
3️⃣ **ESCALATED** — CRITICAL deviations trigger IRB review (72h deadline)`),
    { span: 6 },
    markdown(`### Why This Matters

**No LLM pipeline can provide this:** Every obligation is tracked with a named actor, a deadline, and a tamper-evident record. If the PI doesn't respond, the platform escalates automatically — no human in the loop needed for SLA enforcement.

This is **qhorus** — formal accountability for agentic systems.`)
  )
);
