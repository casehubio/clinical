import { test, expect } from "@playwright/test";

test.describe("Navigation", () => {
  test("all sidebar links are navigable", async ({ page }) => {
    await page.goto("/");
    await page.waitForSelector("[data-component-id]", { timeout: 10_000 });

    // pages-runtime generates navigation with these selectors — may need updating if runtime changes
    const links = page.locator("nav a, [role='navigation'] a, .pages-nav a");
    const linkCount = await links.count();

    // Walk each link and verify it doesn't lead to an empty page
    for (let i = 0; i < linkCount; i++) {
      const link = links.nth(i);
      if (await link.isVisible()) {
        await link.click();
        await page.waitForTimeout(500);

        // Page should have content (not empty)
        const content = page.locator("#app");
        await expect(content).not.toBeEmpty();
      }
    }
  });

  test("guided and explore modes both accessible", async ({ page }) => {
    await page.goto("/");
    await page.waitForSelector("[data-component-id]", { timeout: 10_000 });

    // Navigate to a guided page
    await page.goto("/#page=" + encodeURIComponent("Guided/1. Trial Overview"));
    await page.waitForTimeout(500);
    let body = await page.textContent("body");
    expect(body).toContain("Trial Overview");

    // Navigate to an explore page
    await page.goto("/#page=" + encodeURIComponent("Explore/Trial Dashboard"));
    await page.waitForTimeout(500);
    body = await page.textContent("body");
    expect(body).toContain("Trial Dashboard");
  });
});
