import { page, columns, markdown, html } from "@casehubio/pages-ui";
import { TRIAL_ID } from "../datasets";
import { STEP6_NARRATIVE } from "../narrative";

/**
 * Step 6: AI Decision & Governance
 *
 * Hero layout: two-column display showing "What the AI decided" vs "How the platform governed it".
 *
 * Components:
 * - Narrative markdown
 * - Left column: AI decision breakdown (grade, unexpected, suspected → SUSAR criteria met)
 * - Right column: Governance context (trust score, threshold, gate status)
 *
 * Data comes from GET /api/trials/{trialId}/adverse-events/{aeId}/governance — a single-object
 * response with fields: grade, unexpected, suspected, susarOversightStatus, workerId, capabilityTag,
 * trustScoreAtRouting, thresholdApplied, currentTrustScore, gateStatus.
 *
 * The AE ID is retrieved from sessionStorage (set by Step 5 action button). If not found, shows
 * a prompt to report an AE in Step 5 first.
 *
 * This is NOT a dataset/lookup pattern — the governance endpoint returns a single object. Use
 * html() with fetch() to get the data and render it dynamically.
 */
export const step6Governance = page("6. AI Decision & Governance",
  markdown(`## AI Decision & Platform Governance\n\n${STEP6_NARRATIVE}`),

  // Hero layout: two-column display
  html(`
    <div id="governance-hero" style="margin: 20px 0;">
      <div id="loading-message" style="color: #666; font-size: 14px; padding: 20px;">
        Loading governance context...
      </div>
      <div id="governance-content" style="display: none;">
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px;">
          <!-- Left column: What the AI decided -->
          <div style="border: 2px solid #1976d2; border-radius: 8px; padding: 20px; background: #e3f2fd;">
            <h3 style="margin-top: 0; color: #1976d2;">What the AI Decided</h3>
            <div id="ai-decision" style="font-size: 14px; line-height: 1.8;"></div>
          </div>

          <!-- Right column: How the platform governed it -->
          <div style="border: 2px solid #388e3c; border-radius: 8px; padding: 20px; background: #e8f5e9;">
            <h3 style="margin-top: 0; color: #388e3c;">How the Platform Governed It</h3>
            <div id="governance-context" style="font-size: 14px; line-height: 1.8;"></div>
          </div>
        </div>
      </div>
      <div id="error-message" style="display: none; color: #c62828; font-size: 14px; padding: 20px;"></div>
    </div>
    <script>
      (function() {
        const loadingMsg = document.getElementById('loading-message');
        const content = document.getElementById('governance-content');
        const errorMsg = document.getElementById('error-message');
        const aiDecision = document.getElementById('ai-decision');
        const governanceContext = document.getElementById('governance-context');

        // Retrieve AE ID from sessionStorage (set by Step 5)
        const aeId = sessionStorage.getItem('demo-ae-id');
        if (!aeId) {
          loadingMsg.style.display = 'none';
          errorMsg.style.display = 'block';
          errorMsg.textContent = 'No AE ID found — report a Grade 4 AE in Step 5 first.';
          return;
        }

        // Fetch governance context
        fetch('/api/trials/${TRIAL_ID}/adverse-events/' + aeId + '/governance')
          .then(r => {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
          })
          .then(data => {
            // Left column: AI decision
            aiDecision.innerHTML = \`
              <p><strong>Input Criteria:</strong></p>
              <ul>
                <li>Grade: <strong>\${data.grade}</strong></li>
                <li>Unexpected: <strong>\${data.unexpected ? 'Yes' : 'No'}</strong></li>
                <li>Suspected: <strong>\${data.suspected ? 'Yes' : 'No'}</strong></li>
              </ul>
              <p><strong>Evaluator Output:</strong></p>
              <p>Worker ID: <code>\${data.workerId || 'N/A'}</code></p>
              <p>Capability: <code>\${data.capabilityTag || 'N/A'}</code></p>
              <p style="margin-top: 10px; padding: 10px; background: #fff3e0; border-left: 4px solid #f57c00;">
                <strong>Result:</strong> SUSAR criteria met (Grade 4 + unexpected + suspected)
              </p>
            \`;

            // Right column: Governance context
            const gateStatusDisplay = data.gateStatus === 'PENDING'
              ? '⚠️ PENDING'
              : data.gateStatus === 'APPROVED'
              ? '✅ APPROVED'
              : data.gateStatus || '—';

            governanceContext.innerHTML = \`
              <p><strong>Trust Context:</strong></p>
              <p>Selected Agent: <code>\${data.workerId || 'N/A'}</code></p>
              <p>Trust Score at Routing: <strong>\${data.trustScoreAtRouting !== null ? data.trustScoreAtRouting.toFixed(3) : 'N/A'}</strong></p>
              <p>Threshold Applied: <strong>\${data.thresholdApplied !== null ? data.thresholdApplied.toFixed(3) : 'N/A'}</strong></p>
              <p>Current Trust Score: <strong>\${data.currentTrustScore !== null ? data.currentTrustScore.toFixed(3) : 'N/A'}</strong></p>
              <p style="margin-top: 10px; padding: 10px; background: #ffebee; border-left: 4px solid #d32f2f;">
                <strong>Gate Status:</strong> \${gateStatusDisplay}<br>
                <em>CaseHub's ActionRiskClassifier unconditionally gates all safety decisions. A qualified investigator must approve.</em><br>
                <small style="color: #666;">Regulatory citation: ICH E2A §I.A.1</small>
              </p>
            \`;

            // Show content
            loadingMsg.style.display = 'none';
            content.style.display = 'block';
          })
          .catch(err => {
            loadingMsg.style.display = 'none';
            errorMsg.style.display = 'block';
            errorMsg.textContent = 'Error loading governance context: ' + err.message;
          });
      })();
    </script>
  `),

  // Explanation
  columns(
    { span: 6 },
    markdown(`### The AI's Assessment

The SUSAR evaluator followed a deterministic rule: Grade 4 or 5 + unexpected + suspected relationship = SUSAR criteria met (ICH E2A §I.A.1).

**Key point:** This is not an autonomous decision. The agent produced a recommendation, not an action.`),
    { span: 6 },
    markdown(`### The Platform's Governance

CaseHub's **ActionRiskClassifier** unconditionally gates all safety-related actions. The trust score influenced agent selection, but gate enforcement is absolute — no agent can autonomously report a SUSAR.

**This is the difference between an LLM pipeline and an accountable system.**`)
  )
);
