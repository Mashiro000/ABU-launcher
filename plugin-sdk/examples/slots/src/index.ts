/// <reference path="../../../types/abu-plugin.d.ts" />
globalThis.ABUPlugin = {
  render({ surface }) {
    if (surface === "slot:home.quickActions") return { ui: { type: "button", text: "插槽示例：打开提示", action: "hello" } };
    if (surface === "slot:player.overlay") return { ui: { type: "text", text: "插槽示例已启用", tone: "muted" } };
    return {};
  },
  onAction({ action }) {
    return { ui: { type: "text", text: action === "hello" ? "首页快捷操作成功" : "未知操作" } };
  }
};
