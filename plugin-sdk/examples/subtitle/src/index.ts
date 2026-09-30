/// <reference path="../../../types/abu-plugin.d.ts" />

globalThis.ABUPlugin = {
  render() { return {}; },
  onSubtitle({ title }) {
    if (!title.toLowerCase().includes("elephants dream")) return { value: { subtitles: [] } };
    return { value: { subtitles: [{
      url: "https://dash.akamaized.net/akamai/test/caption_test/ElephantsDream/ElephantsDream_en.vtt"
    }] } };
  }
};
