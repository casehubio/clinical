import { page, columns, metric, table, markdown, lookup, groupBy, col, html } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, deviationsDs } from "../datasets";
import { STEP3_NARRATIVE } from "../narrative";

// Site B ID from DemoDataSeeder — deterministic UUID
const SITE_B_ID = "28d71146-f562-3352-a521-2ede60adba82"; // UUID.nameUUIDFromBytes("SITE-B".getBytes(UTF_8))

/**
 * Step 3: Event — Protocol Deviation Reported
 *
 * Components:
 * - Narrative markdown
 * - Action button: "Report Protocol Deviation" → POST CRITICAL deviation at Site B
 * - Deviations table: type, severity, piApprovalStatus, commandedAt
 * - Commitment lifecycle display: COMMANDED → ...
 *
 * Action button uses html() with inline JavaScript fetch() — casehub-pages has no built-in action button.
 * Button is idempotent: disabled if a CRITICAL deviation already exists at Site B.
 */
export const step3Deviation = page("3. Protocol Deviation",
  markdown(`## Protocol Deviation Event\n\n${STEP3_NARRATIVE}`),

  // Action button with idempotency check
  html(`
    <div id="deviation-action" style="margin: 20px 0;">
      <button id="report-deviation-btn"
              style="background: #d32f2f; color: white; padding: 12px 24px; border: none; border-radius: 4px; font-size: 14px; cursor: pointer; font-weight: 500;">
        Report CRITICAL Protocol Deviation
      </button>
      <p id="deviation-status" style="margin-top: 10px; color: #666; font-size: 14px;"></p>
    </div>
    <script>
      (function() {
        const btn = document.getElementById('report-deviation-btn');
        const status = document.getElementById('deviation-status');

        // Check for existing CRITICAL deviation at Site B
        fetch('/api/trials/${TRIAL_ID}/deviations')
          .then(r => r.json())
          .then(data => {
            const existing = data.find(d => d.siteId === '${SITE_B_ID}' && d.severity === 'CRITICAL');
            if (existing) {
              btn.disabled = true;
              btn.textContent = 'CRITICAL Deviation Already Reported';
              btn.style.background = '#757575';
              btn.style.cursor = 'not-allowed';
              status.textContent = 'Deviation ID: ' + existing.id;
              status.style.color = '#1976d2';
            }
          })
          .catch(err => {
            console.error('Idempotency check failed:', err);
          });

        btn.addEventListener('click', function() {
          btn.disabled = true;
          btn.textContent = 'Reporting...';
          status.textContent = 'Sending request...';
          status.style.color = '#f57c00';

          fetch('/api/trials/${TRIAL_ID}/sites/${SITE_B_ID}/deviations', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
              deviationType: 'DOSING_ERROR',
              severity: 'CRITICAL'
            })
          })
          .then(r => {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
          })
          .then(deviation => {
            btn.textContent = 'Deviation Reported ✓';
            btn.style.background = '#388e3c';
            status.textContent = 'CRITICAL deviation reported — PI COMMANDED. Deviation ID: ' + deviation.id;
            status.style.color = '#2e7d32';

            // Trigger dataset refresh after 1s
            setTimeout(() => {
              window.location.reload();
            }, 1000);
          })
          .catch(err => {
            btn.disabled = false;
            btn.textContent = 'Report CRITICAL Protocol Deviation';
            btn.style.background = '#d32f2f';
            status.textContent = 'Error: ' + err.message;
            status.style.color = '#c62828';
          });
        });
      })();
    </script>
  `),

  // Deviations table
  table({
    sortable: true,
    columns: [
      { id: "deviationType" as ColumnId, label: "Type" },
      { id: "severity" as ColumnId, label: "Severity" },
      { id: "piApprovalStatus" as ColumnId, label: "PI Approval Status",
        expression: 'value === "COMMANDED" ? "⚠️ COMMANDED" : value === "APPROVED" ? "✅ APPROVED" : value === "ESCALATED" ? "🔼 ESCALATED" : value' },
      { id: "commandedAt" as ColumnId, label: "Commanded At",
        expression: 'value != null ? new Date(value).toLocaleString() : "—"' },
      { id: "respondedAt" as ColumnId, label: "Responded At",
        expression: 'value != null ? new Date(value).toLocaleString() : "—"' }
    ],
    lookup: lookup(deviationsDs, [], [])
  }),

  // Commitment lifecycle summary
  columns(
    { span: 4 },
    metric({
      title: "COMMANDED Deviations",
      lookup: lookup(
        deviationsDs,
        [],
        [groupBy([], [col("piApprovalStatus")])]
      )
    }),
    { span: 8 },
    markdown(`### Commitment Lifecycle

The platform sends a formal **COMMAND** to the named Principal Investigator — not a notification, an obligation. A Commitment is created with a 24-hour deadline. If the PI doesn't respond, the platform escalates automatically.

**Status:**
- ⚠️ **COMMANDED** — PI obligation created, deadline active
- ✅ **APPROVED** — PI responded, Commitment closed
- 🔼 **ESCALATED** — IRB committee review triggered (CRITICAL deviations only)`)
  )
);
