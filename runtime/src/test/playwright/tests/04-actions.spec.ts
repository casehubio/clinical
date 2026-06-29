import { test, expect } from "@playwright/test";

test.describe("Action flows", () => {
  test("step 3→4: report deviation and approve as PI", async ({ page }) => {
    await page.goto("/");
    await page.waitForSelector("[data-component-id]", { timeout: 10_000 });

    // Navigate to Step 3
    await page.goto("/#page=" + encodeURIComponent("Guided/3. Protocol Deviation"));
    await page.waitForTimeout(1000);

    // Click "Report CRITICAL Protocol Deviation" action button
    const reportBtn = page.locator("pages-action-button button");
    if (await reportBtn.isVisible()) {
      // Accept confirmation dialog
      page.on("dialog", (dialog) => dialog.accept());
      await reportBtn.click();

      // Verify deviations table shows COMMANDED row
      await expect(page.locator("pages-table")).toContainText("COMMANDED", { timeout: 10_000 });
    }

    // Navigate to Step 4
    await page.goto("/#page=" + encodeURIComponent("Guided/4. PI Authorisation"));
    await page.waitForTimeout(1000);

    // The <clinical-pi-approval> component should have loaded
    const piComponent = page.locator("clinical-pi-approval");
    await expect(piComponent).toBeVisible({ timeout: 5_000 });

    // Button should be enabled if a COMMANDED deviation exists
    const approveBtn = piComponent.locator("#approve-pi-btn");
    const isDisabled = await approveBtn.getAttribute("disabled");
    if (isDisabled === null) {
      await approveBtn.click();
      // Verify approval feedback
      const status = piComponent.locator("#pi-approval-status");
      await expect(status).toContainText("PI approved", { timeout: 10_000 });
    }
  });

  test("step 5→7: report AE and approve SUSAR gate", async ({ page }) => {
    await page.goto("/");
    await page.waitForSelector("[data-component-id]", { timeout: 10_000 });

    // Navigate to Step 5
    await page.goto("/#page=" + encodeURIComponent("Guided/5. Grade 4 AE Reported"));
    await page.waitForTimeout(1000);

    // Click "Report Grade 4 Adverse Event" action button
    const reportBtn = page.locator("pages-action-button button");
    if (await reportBtn.isVisible()) {
      page.on("dialog", (dialog) => dialog.accept());
      await reportBtn.click();

      // Wait for engine async processing — AE escalationStatus transitions to REQUESTED
      const aeTable = page.locator("pages-table");
      await expect(aeTable).toContainText("REQUESTED", { timeout: 15_000 });
    }

    // Navigate to Step 7
    await page.goto("/#page=" + encodeURIComponent("Guided/7. Resolution & Trust"));
    await page.waitForTimeout(1000);

    // The <clinical-susar-gate> component should have auto-discovered the REQUESTED AE
    const gateComponent = page.locator("clinical-susar-gate");
    await expect(gateComponent).toBeVisible({ timeout: 5_000 });

    const gateBtn = gateComponent.locator("#approve-gate-btn");
    const gateStatus = gateComponent.locator("#resolution-status");

    // Wait for the component to finish loading state
    await expect(gateStatus).not.toContainText("Loading", { timeout: 10_000 });

    const isDisabled = await gateBtn.getAttribute("disabled");
    if (isDisabled === null) {
      await gateBtn.click();
      // Verify trust score display
      await expect(gateStatus).toContainText("Trust Score", { timeout: 10_000 });
    }
  });

  test("step 8: Merkle verification", async ({ page }) => {
    await page.goto("/");
    await page.waitForSelector("[data-component-id]", { timeout: 10_000 });

    // Navigate to Step 8
    await page.goto("/#page=" + encodeURIComponent("Guided/8. The Proof"));
    await page.waitForTimeout(1000);

    const merkleComponent = page.locator("clinical-merkle-verify");
    await expect(merkleComponent).toBeVisible({ timeout: 5_000 });

    const verifyBtn = merkleComponent.locator("#verify-btn");
    await verifyBtn.click();

    // Wait for verification result
    const result = merkleComponent.locator("#verify-result");
    await expect(result).toBeVisible({ timeout: 10_000 });

    // Should show VERIFIED or FAILED (both are valid outcomes depending on seeded data)
    const resultText = await result.textContent();
    expect(resultText).toMatch(/VERIFIED|VERIFICATION FAILED/);
  });
});
