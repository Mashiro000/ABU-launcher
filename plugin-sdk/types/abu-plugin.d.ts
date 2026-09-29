export type UiNode =
  | { type: "column" | "row" | "card" | "list"; id?: string; children: UiNode[] }
  | { type: "text"; id?: string; text: string; tone?: "default" | "muted" | "accent" | "danger" }
  | { type: "button"; id?: string; text: string; action: string; tone?: "default" | "accent" | "danger" }
  | { type: "input"; id: string; text?: string; hint?: string; value?: string }
  | { type: "toggle"; id: string; text?: string; value?: "true" | "false" }
  | { type: "image"; id?: string; text?: string; asset: `assets/${string}` }
  | { type: "progress"; id?: string; progress: number }
  | { type: "spacer"; id?: string };

export type Surface = "home" | "settings" | "player" | `slot:${string}`;

export interface PluginManifest {
  schemaVersion: 1;
  id: string;
  name: string;
  version: string;
  author: string;
  description?: string;
  kind: "ui" | "system" | "data_source" | "subtitle";
  entry: string;
  hostApi: string;
  surfaces?: Array<"home" | "settings" | "player">;
  slots?: Array<"home.quickActions" | "player.overlay">;
  permissions?: Array<{ id: "storage" | "network"; title: string; sensitive?: boolean }>;
  networkDomains?: string[];
  services?: Array<{ name: string; version: number }>;
}

export interface CapabilityRequest {
  id: string;
  capability: "device.info" | "storage.get" | "storage.set" | "network.fetch" | "events.publish" | "services.register" | "services.resolve" | "services.call";
  arguments?: Record<string, unknown>;
}

export interface PluginOutput {
  ui?: UiNode;
  capabilities?: CapabilityRequest[];
  navigation?: { push?: string; pop?: boolean };
  value?: Record<string, unknown>;
}

export interface AbuPlugin {
  render(input: { surface: Surface; route?: string }): PluginOutput;
  onAction?(input: { surface: Surface; route?: string; action: string; values?: Record<string, string> }): PluginOutput;
  onCapabilities?(input: { results: Array<{ id: string; ok: boolean; value?: unknown; error?: string }> }): PluginOutput;
  onEvent?(input: { sourcePluginId: string; topic: string; payload: unknown }): PluginOutput;
  onService?(input: { callerPluginId: string; service: string; method: string; arguments: Record<string, unknown> }): PluginOutput;
}

declare global { var ABUPlugin: AbuPlugin; }
