import { loadSite } from "@casehubio/pages-runtime";
import { dashboard } from "./dashboard";
import { theme } from "./theme";
import { ClinicalPiApproval } from "./components/clinical-pi-approval";
import { ClinicalSusarGate } from "./components/clinical-susar-gate";
import { ClinicalMerkleVerify } from "./components/clinical-merkle-verify";

customElements.define("clinical-pi-approval", ClinicalPiApproval);
customElements.define("clinical-susar-gate", ClinicalSusarGate);
customElements.define("clinical-merkle-verify", ClinicalMerkleVerify);

const style = document.createElement("style");
style.textContent = theme;
document.head.appendChild(style);

const container = document.getElementById("app");
if (container) {
  loadSite(container, dashboard).catch(console.error);
}
