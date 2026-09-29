/// <reference path="../../../types/abu-plugin.d.ts" />

globalThis.ABUPlugin = {
  render() { return { ui: { type: "column", children: [
    { type: "text", text: "跨插件搜索", tone: "accent" },
    { type: "input", id: "query", text: "关键字", hint: "输入电影或频道" },
    { type: "button", text: "搜索", action: "search" }
  ] } }; },
  onAction({ action, values }) {
    if (action !== "search") return this.render({ surface: "home" });
    return { capabilities: [{ id: "search", capability: "services.call", arguments: {
      owner: "com.example.service-provider", name: "library", minimumVersion: 1,
      method: "search", arguments: { query: values?.query || "" }
    } }] };
  },
  onCapabilities({ results }) {
    const result = results[0];
    if (!result?.ok) return { ui: { type: "text", text: `服务不可用：${result?.error}`, tone: "danger" } };
    const data = result.value as { titles?: string[] } | undefined;
    return { ui: { type: "column", children: [
      { type: "text", text: "搜索结果", tone: "accent" },
      { type: "list", children: (data?.titles || []).map(title => ({ type: "card", children: [{ type: "text", text: title }] })) },
      { type: "button", text: "再搜一次", action: "reset" }
    ] } };
  }
};
