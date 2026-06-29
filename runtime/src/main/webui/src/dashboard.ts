import { page, tree, markdown } from "@casehubio/pages-ui";

const placeholder = (name: string) => page(name, markdown(`*${name} — coming soon*`));

export const dashboard = page("CaseHub Clinical",
  tree(
    ["Guided/1. Trial Overview", placeholder("Trial Overview")],
    ["Guided/2. Meet the AI Agents", placeholder("AI Agents")],
    ["Guided/3. Protocol Deviation", placeholder("Deviation")],
    ["Guided/4. PI Authorisation", placeholder("PI Auth")],
    ["Guided/5. Grade 4 AE Reported", placeholder("AE Event")],
    ["Guided/6. AI Decision & Governance", placeholder("Governance")],
    ["Guided/7. Resolution & Trust", placeholder("Resolution")],
    ["Guided/8. The Proof", placeholder("The Proof")],
    ["Explore/Trial Dashboard", placeholder("Trial Dashboard")],
    ["Explore/Adverse Events", placeholder("Adverse Events")],
    ["Explore/Audit Trail", placeholder("Audit Trail")],
    ["Explore/Protocol Deviations", placeholder("Deviations")],
    ["Explore/Trust Network", placeholder("Trust Network")],
    ["Explore/Site Detail", placeholder("Site Detail")]
  )
);
