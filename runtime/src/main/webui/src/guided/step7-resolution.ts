import { page, columns, metric, markdown, html, lookup, groupBy, col } from "@casehubio/pages-ui";
import type { ColumnId } from "@casehubio/data";
import { TRIAL_ID, adverseEventsDs } from "../datasets";
import { STEP7_NARRATIVE } from "../narrative";

/**
 * Step 7: Resolution & Trust Update
 *
 * Components:
 * - Narrative markdown
 * - Action button: "Approve SUSAR Determination" → POST to demo endpoint
 * - Gate decision display (APPROVED with investigator ID)
 * - Attestation card (ENDORSED → safety-accuracy dimension)
 * - Trust score before/after metrics
 * - Regulatory submission status
 *
 * The demo endpoint completes the SUSAR oversight gate WorkItem, triggering the real chain:
 * ActionGateApprovedEvent → SusarGateDecisionListener → SusarAgentAttestationWriter → TrustScoreJob.
 *
 * The AE ID is retrieved from sessionStorage (set by Step 5 action button).
 */
export const step7Resolution = page("7. Resolution & Trust Update",
  markdown(`## Gate Approval & Trust Score Update\n\n${STEP7_NARRATIVE}`),

  // Action button with idempotency check
  html(`
    <div id="resolution-action" style="margin: 20px 0;">
      <button id="approve-gate-btn"
              style="background: #388e3c; color: white; padding: 12px 24px; border: none; border-radius: 4px; font-size: 14px; cursor: pointer; font-weight: 500;">
        Approve SUSAR Determination
      </button>
      <p id="resolution-status" style="margin-top: 10px; color: #666; font-size: 14px;"></p>
    </div>
    <script>
      (function() {
        const btn = document.getElementById('approve-gate-btn');
        const status = document.getElementById('resolution-status');

        // Retrieve AE ID from sessionStorage (set by Step 5)
        const aeId = sessionStorage.getItem('demo-ae-id');
        if (!aeId) {
          btn.disabled = true;
          btn.textContent = 'No AE to Approve';
          btn.style.background = '#757575';
          btn.style.cursor = 'not-allowed';
          status.textContent = 'Report a Grade 4 AE in Step 5 first.';
          status.style.color = '#c62828';
          return;
        }

        // Check if gate is already approved
        fetch('/api/trials/${TRIAL_ID}/adverse-events')
          .then(r => r.json())
          .then(data => {
            const ae = data.find(ae => ae.id === aeId);
            if (ae && ae.susarOversightStatus === 'COMPLETED') {
              btn.disabled = true;
              btn.textContent = 'SUSAR Gate Already Approved ✓';
              btn.style.background = '#757575';
              btn.style.cursor = 'not-allowed';
              status.textContent = 'Gate approval complete.';
              status.style.color = '#2e7d32';
            } else if (ae && ae.susarOversightStatus !== 'REQUESTED') {
              btn.disabled = true;
              btn.textContent = 'Gate Not Ready';
              btn.style.background = '#757575';
              btn.style.cursor = 'not-allowed';
              status.textContent = 'SUSAR oversight not in REQUESTED state.';
              status.style.color = '#f57c00';
            }
          })
          .catch(err => {
            console.error('Idempotency check failed:', err);
          });

        btn.addEventListener('click', function() {
          btn.disabled = true;
          btn.textContent = 'Approving...';
          status.textContent = 'Completing gate WorkItem...';
          status.style.color = '#f57c00';

          fetch('/api/demo/adverse-events/' + aeId + '/approve-susar-gate', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'}
          })
          .then(r => {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
          })
          .then(result => {
            btn.textContent = 'SUSAR Gate Approved ✓';
            btn.style.background = '#2e7d32';
            status.innerHTML = \`
              <strong>Gate Decision:</strong> \${result.gateDecision || 'APPROVED'}<br>
              <strong>Investigator ID:</strong> \${result.investigatorId || 'demo-investigator'}<br>
              <strong>Attestation:</strong> \${result.attestation || 'ENDORSED'} → safety-accuracy dimension<br>
              <strong>Trust Score Before:</strong> \${result.trustScoreBefore !== null ? result.trustScoreBefore.toFixed(3) : 'N/A'}<br>
              <strong>Trust Score After:</strong> \${result.trustScoreAfter !== null ? result.trustScoreAfter.toFixed(3) : 'N/A'}<br>
              <strong>Regulatory Submission:</strong> IND report created
            \`;
            status.style.color = '#2e7d32';

            // Trigger dataset refresh after 1s
            setTimeout(() => {
              window.location.reload();
            }, 1000);
          })
          .catch(err => {
            btn.disabled = false;
            btn.textContent = 'Approve SUSAR Determination';
            btn.style.background = '#388e3c';
            status.textContent = 'Error: ' + err.message;
            status.style.color = '#c62828';
          });
        });
      })();
    </script>
  `),

  // Metrics: trust score before/after, gate status, attestation
  columns(
    { span: 3 },
    metric({
      title: "SUSAR Gates Completed",
      lookup: lookup(
        adverseEventsDs,
        [],
        [groupBy([], [col("susarOversightStatus")])]
      )
    }),
    { span: 3 },
    markdown(`### Attestation Recorded

When the investigator approves the SUSAR determination, CaseHub writes a **LedgerAttestation** entry with verdict ENDORSED. This feeds into the agent's Bayesian trust score computation.`),
    { span: 3 },
    markdown(`### Trust Score Recomputation

The demo endpoint triggers \`TrustScoreJob.runComputation()\` immediately after the attestation is written. The trust score delta is computed from the new attestation — good decisions build trust.`),
    { span: 3 },
    markdown(`### Regulatory Submission

The IND report work item is created automatically with a 7-day deadline (21 CFR 312.32). The regulatory team receives the work item via routing policy.`)
  ),

  // Explanation
  markdown(`### The Trust Feedback Loop

This is Layer 7 in action: trust-weighted routing with attestation feedback. The agent was selected by trust score, its decision was gated, and the investigator's approval became an attestation that updated the trust score.

**Key insight:** Trust scores are not static agent metadata — they are continuously refined by human attestations. The platform learns which agents make reliable decisions over time.`)
);
