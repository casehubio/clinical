import { loadSite } from "@casehubio/pages-runtime";
import { dashboard } from "./dashboard";
import { theme } from "./theme";

// Inject theme
const style = document.createElement("style");
style.textContent = theme;
document.head.appendChild(style);

const container = document.getElementById("app");
if (container) {
  loadSite(container, dashboard).catch(console.error);
}
