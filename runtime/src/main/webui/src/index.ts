import { loadSite } from "@casehubio/pages-runtime";
import { dashboard } from "./dashboard";
import { theme } from "./theme";

// Inject theme
const style = document.createElement("style");
style.textContent = theme;
document.head.appendChild(style);

const container = document.getElementById("app");
if (container) {
  // pages-runtime renders html() content via innerHTML, which doesn't execute
  // <script> tags. This observer re-creates script elements so they run.
  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        if (node instanceof HTMLElement) {
          node.querySelectorAll("script").forEach((old) => {
            const fresh = document.createElement("script");
            fresh.textContent = old.textContent;
            old.parentNode?.replaceChild(fresh, old);
          });
        }
      }
    }
  });
  observer.observe(container, { childList: true, subtree: true });

  loadSite(container, dashboard).catch(console.error);
}
