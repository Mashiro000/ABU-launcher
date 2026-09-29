/// <reference path="../../../types/abu-plugin.d.ts" />

globalThis.ABUPlugin = {
  render() {
    return {
      ui: {
        type: "column",
        children: [
          { type: "text", text: "Hello ABU", tone: "accent" },
          { type: "text", text: "这个页面由第三方插件提供。", tone: "muted" },
          { type: "button", text: "读取设备信息", action: "device", tone: "accent" }
        ]
      }
    };
  },
  onAction(input) {
    if (input.action === "device") {
      return { capabilities: [{ id: "device", capability: "device.info", arguments: {} }] };
    }
    return this.render({ surface: "home" });
  },
  onCapabilities(input) {
    const result = input.results[0];
    return {
      ui: {
        type: "card",
        children: [
          { type: "text", text: result?.ok ? `设备信息：${JSON.stringify(result.value)}` : `调用失败：${result?.error}`, tone: result?.ok ? "accent" : "danger" },
          { type: "button", text: "返回", action: "back" }
        ]
      }
    };
  }
};
