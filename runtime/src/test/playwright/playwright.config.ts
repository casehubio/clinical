import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 30_000,
  retries: 0,
  workers: 1,
  reporter: "list",
  use: {
    baseURL: "http://localhost:8080",
    headless: true,
    viewport: { width: 1440, height: 900 },
  },
  webServer: {
    command: "mvn -f ../../../pom.xml quarkus:dev -Dquarkus.http.host=0.0.0.0",
    url: "http://localhost:8080/q/health",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
