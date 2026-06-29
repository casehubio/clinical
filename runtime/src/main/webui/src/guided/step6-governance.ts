import { page, columns, markdown } from "@casehubio/pages-ui";
import { STEP6_NARRATIVE } from "../narrative";
import { alert } from "../helpers";

/**
 * Step 6: AI Decision & Governance
 *
 * Hero layout: two-column static display showing "What the AI decided" vs "How the platform governed it".
 *
 * Components:
 * - Narrative markdown
 * - Info alert: SUSAR criteria evaluation summary
 * - Left column: AI decision breakdown (grade, unexpected, suspected → SUSAR criteria met)
 * - Right column: Governance context (trust routing, threshold, gate enforcement)
 */
export const step6Governance = page("6. AI Decision & Governance",
  markdown(`## AI Decision & Platform Governance\n\n${STEP6_NARRATIVE}`),

  // SUSAR criteria evaluation summary
  alert({
    severity: "warning",
    content: "SUSAR criteria met: Grade 4 + unexpected + suspected relationship (ICH E2A §I.A.1). The platform's ActionRiskClassifier unconditionally gates this decision — no agent can autonomously report a SUSAR.",
    dismissible: false
  }),

  // Explanation
  columns(
    [6, 6],
    [markdown(`### The AI's Assessment

The SUSAR evaluator followed a deterministic rule: Grade 4 or 5 + unexpected + suspected relationship = SUSAR criteria met (ICH E2A §I.A.1).

**Key point:** This is not an autonomous decision. The agent produced a recommendation, not an action.`)],
    [markdown(`### The Platform's Governance

CaseHub's **ActionRiskClassifier** unconditionally gates all safety-related actions. The trust score influenced agent selection, but gate enforcement is absolute — no agent can autonomously report a SUSAR.

**This is the difference between an LLM pipeline and an accountable system.**`)]
  )
);
