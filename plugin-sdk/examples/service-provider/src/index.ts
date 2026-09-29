/// <reference path="../../../types/abu-plugin.d.ts" />

globalThis.ABUPlugin = {
  render() { return { ui: { type: "text", text: "Library service ready" } }; },
  onService({ callerPluginId, service, method, arguments: args }) {
    // A provider can apply its own allow-list to callerPluginId.
    if (service !== "library" || method !== "search") return { value: { error: "unsupported method" } };
    const query = String(args.query || "").toLowerCase().slice(0, 100);
    const titles = ["ABU Demo Movie", "ABU Demo Series", "ABU Live Channel"];
    return { value: { callerPluginId, titles: titles.filter(title => title.toLowerCase().includes(query)) } };
  }
};
