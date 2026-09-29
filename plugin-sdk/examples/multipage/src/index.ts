/// <reference path="../../../types/abu-plugin.d.ts" />

function home(name: string): import("../../../types/abu-plugin").PluginOutput {
  return { ui: { type: "column", children: [
    { type: "text", text: `欢迎，${name || "访客"}`, tone: "accent" },
    { type: "list", children: [
      { type: "card", children: [{ type: "text", text: "这是插件内的滚动列表" }] },
      { type: "card", children: [{ type: "text", text: "状态保存在宿主隔离存储中" }] }
    ] },
    { type: "button", text: "设置显示名称", action: "open-settings" }
  ] } };
}

globalThis.ABUPlugin = {
  render({ route }) {
    if (route === "settings") return { ui: { type: "column", children: [
      { type: "text", text: "设置", tone: "accent" },
      { type: "input", id: "displayName", text: "显示名称", hint: "输入名称" },
      { type: "button", text: "保存并返回", action: "save" }
    ] } };
    return { capabilities: [{ id: "load-name", capability: "storage.get", arguments: { key: "displayName" } }] };
  },
  onAction({ action, values }) {
    if (action === "open-settings") return { navigation: { push: "settings" } };
    if (action === "save") return {
      navigation: { pop: true },
      capabilities: [{ id: "save-name", capability: "storage.set", arguments: { key: "displayName", value: values?.displayName || "" } }]
    };
    return home("");
  },
  onCapabilities({ results }) {
    const result = results[0];
    if (result?.id === "load-name") {
      if (!result.ok) return { ui: { type: "text", text: `读取失败：${result.error}`, tone: "danger" } };
      const value = result.value as { value?: string | null } | undefined;
      return home(value?.value || "");
    }
    return result?.ok ? {} : { ui: { type: "text", text: `保存失败：${result?.error}`, tone: "danger" } };
  }
};
