import type { Component } from "@casehubio/pages-ui";

export function actionButton(props: Record<string, unknown>): Component {
  return Object.freeze({ type: "action-button", props: Object.freeze(props) }) as Component;
}

export function alert(props: Record<string, unknown>): Component {
  return Object.freeze({ type: "alert", props: Object.freeze(props) }) as Component;
}
