/// <reference path="../../../types/abu-plugin.d.ts" />

type Sample = { title?: string; prog?: string };

globalThis.ABUPlugin = {
  render() { return {}; },
  onDataSource({ operation, query, cursor }) {
    if (operation !== "list") return { value: { error: "Unsupported operation" } };
    const page = Math.max(0, Math.min(100, Number(cursor) || 0));
    return { capabilities: [{
      id: JSON.stringify({ query: query.slice(0, 100), page }),
      capability: "network.fetch",
      arguments: { url: "https://storage.googleapis.com/cpe-sample-media/content.json" }
    }] };
  },
  onCapabilities({ results }) {
    const result = results[0];
    if (!result?.ok) return { value: { error: result?.error || "Network request failed" } };
    const response = result.value as { status: number; body: string };
    if (response.status !== 200) return { value: { error: `Catalog HTTP ${response.status}` } };
    try {
      const { query, page } = JSON.parse(result.id) as { query: string; page: number };
      const catalog = JSON.parse(response.body) as Record<string, Sample>;
      const all = Object.entries(catalog)
        .filter(([, item]) => item.title && item.prog && item.title.toLowerCase().includes(query.toLowerCase()))
        .map(([id, item]) => ({ id, title: item.title!, streamUrl: item.prog! }));
      const size = 8;
      return { value: {
        items: all.slice(page * size, (page + 1) * size),
        nextCursor: (page + 1) * size < all.length ? String(page + 1) : null
      } };
    } catch (error) {
      return { value: { error: `Invalid catalog: ${String(error)}` } };
    }
  }
};
