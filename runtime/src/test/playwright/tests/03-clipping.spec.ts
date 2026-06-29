import { test, expect } from "@playwright/test";

const VIEWPORTS = [
  { width: 1440, height: 900, label: "1440x900" },
  { width: 1920, height: 1080, label: "1920x1080" },
];

const PAGES_TO_CHECK = [
  "Guided/1. Trial Overview",
  "Guided/3. Protocol Deviation",
  "Guided/5. Grade 4 AE Reported",
  "Explore/Trial Dashboard",
  "Explore/Adverse Events",
];

test.describe("Clipping checks", () => {
  for (const viewport of VIEWPORTS) {
    for (const pagePath of PAGES_TO_CHECK) {
      test(`${pagePath} has no overflow at ${viewport.label}`, async ({ page }) => {
        await page.setViewportSize(viewport);
        await page.goto("/");
        await page.waitForSelector("[data-component-id]", { timeout: 10_000 });

        await page.goto("/#page=" + encodeURIComponent(pagePath));
        await page.waitForTimeout(1000);

        const overflow = await page.evaluate(() => {
          const app = document.getElementById("app");
          if (!app) return { clipped: false };
          return {
            clipped: app.scrollWidth > app.clientWidth,
            scrollWidth: app.scrollWidth,
            clientWidth: app.clientWidth,
          };
        });

        expect(overflow.clipped).toBe(false);
      });
    }
  }
});
