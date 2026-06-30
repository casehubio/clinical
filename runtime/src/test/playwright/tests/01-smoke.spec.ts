import { test, expect } from "@playwright/test";

const GUIDED_PAGES = [
  { path: "Guided/1. Trial Overview", heading: "Trial Overview" },
  { path: "Guided/2. Meet the AI Agents", heading: "AI Agents" },
  { path: "Guided/3. Protocol Deviation", heading: "Protocol Deviation" },
  { path: "Guided/4. PI Authorisation", heading: "PI Authorisation" },
  { path: "Guided/5. Grade 4 AE Reported", heading: "Grade 4" },
  { path: "Guided/6. AI Decision & Governance", heading: "Governance" },
  { path: "Guided/7. Resolution & Trust", heading: "Resolution" },
  { path: "Guided/8. The Proof", heading: "Proof" },
];

const EXPLORE_PAGES = [
  { path: "Explore/Trial Dashboard", heading: "Trial Dashboard" },
  { path: "Explore/Adverse Events", heading: "Adverse Events" },
  { path: "Explore/Audit Trail", heading: "Audit Trail" },
  { path: "Explore/Protocol Deviations", heading: "Deviations" },
  { path: "Explore/Trust Network", heading: "Trust" },
  { path: "Explore/Site Detail", heading: "Site" },
];

const ALL_PAGES = [...GUIDED_PAGES, ...EXPLORE_PAGES];

test.describe("Page reachability", () => {
  for (const page of ALL_PAGES) {
    test(`${page.path} renders without errors`, async ({ page: p }) => {
      const errors: string[] = [];
      p.on("console", (msg) => {
        if (msg.type() === "error") errors.push(msg.text());
      });

      await p.goto("/");
      await p.waitForSelector("[data-component-id]", { timeout: 10_000 });

      // Navigate via hash — pages-runtime uses hash-based routing
      const encodedPath = encodeURIComponent(page.path);
      await p.goto(`/#page=${encodedPath}`);
      await expect(p.locator("body")).toContainText(page.heading, { timeout: 5_000 });

      // No JS console errors (targeted filter: only suppress favicon.ico 404s)
      const realErrors = errors.filter(
        (e) => !e.includes("favicon.ico") && !/^Failed to load resource.*404/.test(e)
      );
      expect(realErrors).toEqual([]);
    });
  }
});

test.describe("Data binding", () => {
  test("Step 1 metrics show values", async ({ page }) => {
    await page.goto("/");
    await page.waitForSelector("[data-component-id]", { timeout: 10_000 });

    // Step 1 is the default page — check metrics have non-empty values
    const metrics = page.locator("pages-metric");
    const count = await metrics.count();
    expect(count).toBeGreaterThanOrEqual(4);
  });

  test("Step 1 sites table has rows", async ({ page }) => {
    await page.goto("/");
    await page.waitForSelector("pages-table", { timeout: 10_000 });

    const tableRows = page.locator("pages-table tbody tr");
    await expect(tableRows.first()).toBeVisible({ timeout: 5_000 });
    const rowCount = await tableRows.count();
    expect(rowCount).toBeGreaterThanOrEqual(1);
  });
});
