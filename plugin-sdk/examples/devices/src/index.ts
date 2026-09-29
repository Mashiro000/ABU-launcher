/// <reference path="../../../types/abu-plugin.d.ts" />
globalThis.ABUPlugin = {
  render() {
    return { ui: { type: "column", children: [
      { type: "text", text: "设备清单（只读）", tone: "accent" },
      { type: "button", text: "刷新 USB 和蓝牙", action: "refresh" }
    ] } };
  },
  onAction({ action }) {
    if (action !== "refresh") return {};
    return { capabilities: [
      { id: "usb", capability: "usb.list", arguments: {} },
      { id: "bluetooth", capability: "bluetooth.list", arguments: {} }
    ] };
  },
  onCapabilities({ results }) {
    return { ui: { type: "column", children: [
      { type: "text", text: "设备查询结果", tone: "accent" },
      ...results.map((result) => ({ type: "text" as const, text: `${result.id}: ${result.ok ? JSON.stringify(result.value) : result.error || "已拒绝"}` })),
      { type: "button", text: "再次刷新", action: "refresh" }
    ] } };
  },
  onEvent({ topic }) {
    if (topic.startsWith("device.")) return { ui: { type: "text", text: `设备变化：${topic}，请刷新清单` } };
    return {};
  }
};
