export function onTableSelection(
  el: HTMLElement,
  sourceDataset: string,
  callback: (entityId: string, trialId: string) => void,
): () => void {
  const handler = (e: Event) => {
    const detail = (e as CustomEvent).detail;
    const rows = detail?.selectedRows ?? [];
    if (!rows.length) return;
    const row = rows[0];
    try {
      const id = row.cell("id");
      if (!id || id.type === "NULL") return;
      const testCol = sourceDataset === "adverse-events" ? "grade" : "deviationType";
      const test = row.cell(testCol);
      if (!test || test.type === "NULL") return;
      const trialId = el.getAttribute("data-trial-id") ?? "";
      callback(String(id.value), trialId);
    } catch { /* not our dataset */ }
  };
  document.addEventListener("selection-change", handler);
  return () => document.removeEventListener("selection-change", handler);
}
