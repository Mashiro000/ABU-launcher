export type UiNode =
  | { type: "column" | "row" | "card"; id?: string; children: UiNode[] }
  | { type: "text"; id?: string; text: string; tone?: "default" | "muted" | "accent" | "danger" }
  | { type: "button"; id?: string; text: string; action: string; tone?: "default" | "accent" | "danger" }
  | { type: "spacer"; id?: string };

export interface CapabilityRequest {
  id: string;
  capability: "device.info" | "storage.get" | "storage.set" | "network.fetch" | "usb.list" | "bluetooth.list" | "events.publish" | "services.register" | "services.resolve";
  arguments?: Record<string, unknown>;
}

export interface PluginOutput {
  ui?: UiNode;
  capabilities?: CapabilityRequest[];
}

export interface AbuPlugin {
  render(input: { surface: "home" | "settings" | "player" }): PluginOutput;
  onAction?(input: { surface: string; action: string }): PluginOutput;
  onCapabilities?(input: { results: Array<{ id: string; ok: boolean; value?: unknown; error?: string }> }): PluginOutput;
  onEvent?(input: { sourcePluginId: string; topic: string; payload: unknown }): PluginOutput;
}

declare global { var ABUPlugin: AbuPlugin; }
