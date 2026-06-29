import { page, columns, metric, table, markdown, lookup, groupBy, col, html } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, adverseEventsDs } from "../datasets";
import { STEP5_NARRATIVE } from "../narrative";

// Site B and Patient B1 IDs from DemoDataSeeder — deterministic UUIDs
const SITE_B_ID = "28d71146-f562-3352-a521-2ede60adba82"; // UUID.nameUUIDFromBytes("SITE-B".getBytes(UTF_8))
const PATIENT_B1_ID = "4bb87f70-ca9e-3ded-9bbc-df9bf6fbb38d"; // UUID.nameUUIDFromBytes("PATIENT-B-001".getBytes(UTF_8))

/**
 * Step 5: Event — Grade 4 AE Reported
 *
 * Components:
 * - Narrative markdown (includes DSMB rollup callout)
 * - Action button: "Report Adverse Event" → POST Grade 4 unexpected AE at Site B
 * - AE detail display with SLA deadline
 * - AE table with polling (3s refresh during active scenario)
 *
 * Action button uses html() with inline JavaScript fetch() — casehub-pages has no built-in action button.
 * Button is idempotent: disabled if a Grade 4 AE already exists at Site B for Patient B1.
 *
 * DSMB rollup context: The seeder leaves grade4Active flags set on the trial case blackboard for
 * the seeded Grade 4 AEs at Site A. When this new Grade 4 AE fires at Site B, the trial case now
 * sees ≥2 sites with simultaneous Grade 4+ signals — the DSMB rollup binding fires automatically.
 */
export const step5AeEvent = page("5. Grade 4 AE Reported",
  markdown(`## Grade 4 Adverse Event\n\n${STEP5_NARRATIVE}

**Notice:** The platform detected a cross-site safety pattern. Two sites now have active Grade 4+ events — a DSMB review has been triggered automatically, with no site-level agent having global visibility.`),

  // Action button with idempotency check
  html(`
    <div id="ae-action" style="margin: 20px 0;">
      <button id="report-ae-btn"
              style="background: #d32f2f; color: white; padding: 12px 24px; border: none; border-radius: 4px; font-size: 14px; cursor: pointer; font-weight: 500;">
        Report Grade 4 Adverse Event
      </button>
      <p id="ae-status" style="margin-top: 10px; color: #666; font-size: 14px;"></p>
    </div>
    <script>
      (function() {
        const btn = document.getElementById('report-ae-btn');
        const status = document.getElementById('ae-status');

        // Check for existing Grade 4 AE at Site B for Patient B1
        fetch('/api/trials/${TRIAL_ID}/adverse-events')
          .then(r => r.json())
          .then(data => {
            const existing = data.find(ae => ae.enrollmentId === '${PATIENT_B1_ID}' && ae.grade === 'GRADE_4');
            if (existing) {
              btn.disabled = true;
              btn.textContent = 'Grade 4 AE Already Reported';
              btn.style.background = '#757575';
              btn.style.cursor = 'not-allowed';
              status.textContent = 'AE ID: ' + existing.id;
              status.style.color = '#1976d2';
              // Store AE ID for Step 6 governance lookup
              sessionStorage.setItem('demo-ae-id', existing.id);
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

          fetch('/api/trials/${TRIAL_ID}/sites/${SITE_B_ID}/patients/${PATIENT_B1_ID}/adverse-events', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
              grade: 'GRADE_4',
              occurredAt: new Date().toISOString(),
              unexpected: true,
              suspected: true
            })
          })
          .then(r => {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
          })
          .then(ae => {
            btn.textContent = 'AE Reported ✓';
            btn.style.background = '#388e3c';
            status.textContent = 'Grade 4 AE reported — 24h SLA activated. AE ID: ' + ae.id;
            status.style.color = '#2e7d32';
            // Store AE ID for Step 6 governance lookup
            sessionStorage.setItem('demo-ae-id', ae.id);

            // Trigger dataset refresh after 1s
            setTimeout(() => {
              window.location.reload();
            }, 1000);
          })
          .catch(err => {
            btn.disabled = false;
            btn.textContent = 'Report Grade 4 Adverse Event';
            btn.style.background = '#d32f2f';
            status.textContent = 'Error: ' + err.message;
            status.style.color = '#c62828';
          });
        });
      })();
    </script>
  `),

  // AE detail cards (metrics)
  columns(
    { span: 3 },
    metric({
      title: "Grade 4+ AEs",
      lookup: lookup(
        adverseEventsDs,
        [],
        [groupBy([], [col("grade")])]
      )
    }),
    { span: 3 },
    metric({
      title: "SUSAR Oversight Active",
      lookup: lookup(
        adverseEventsDs,
        [],
        [groupBy([], [col("susarOversightStatus")])]
      )
    }),
    { span: 3 },
    metric({
      title: "Escalated AEs",
      lookup: lookup(
        adverseEventsDs,
        [],
        [groupBy([], [col("escalationStatus")])]
      )
    }),
    { span: 3 },
    markdown(`### 24h SLA Active

Grade 4 adverse events trigger a 24-hour SLA work item under ICH E6(R3) §5.17. The platform tracks deadlines automatically — no human monitoring required.`)
  ),

  // AE table
  table({
    sortable: true,
    columns: [
      { id: "enrollmentId" as ColumnId, label: "Patient ID",
        expression: 'value.substring(0, 8) + "..."' },
      { id: "grade" as ColumnId, label: "Grade",
        expression: 'value === "GRADE_4" ? "🔴 GRADE 4" : value === "GRADE_5" ? "⚫ GRADE 5" : value' },
      { id: "unexpected" as ColumnId, label: "Unexpected" },
      { id: "suspected" as ColumnId, label: "Suspected" },
      { id: "escalationStatus" as ColumnId, label: "Escalation Status",
        expression: 'value === "REPORTED" ? "⏳ REPORTED" : value === "ESCALATED" ? "🔼 ESCALATED" : value' },
      { id: "susarOversightStatus" as ColumnId, label: "SUSAR Oversight",
        expression: 'value === "REQUESTED" ? "⚠️ REQUESTED" : value === "COMPLETED" ? "✅ COMPLETED" : value || "—"' },
      { id: "reportedAt" as ColumnId, label: "Reported At",
        expression: 'new Date(value).toLocaleString()' }
    ],
    lookup: lookup(adverseEventsDs, [], [])
  }),

  // DSMB rollup callout
  markdown(`### DSMB Cross-Site Detection

The seeded Grade 4 AEs at Site A left \`grade4Active\` flags set on the trial case blackboard. When this new Grade 4 AE fired at Site B, the trial case detected ≥2 sites with simultaneous Grade 4+ signals — triggering a DSMB review binding automatically.

**This is Layer 6 in action:** trial-level blackboard aggregation with cross-site pattern detection. No site-level agent has global visibility — the engine detects the pattern from accumulated context.`)
);
