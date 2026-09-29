import { cp, mkdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const args = process.argv.slice(2);
const option = (name) => args[args.indexOf(name) + 1];
const id = args.includes("--id") ? option("--id") : undefined;
const name = args.includes("--name") ? option("--name") : undefined;
const author = args.includes("--author") ? option("--author") : "Your Name";
const output = args.includes("--out") ? resolve(option("--out")) : resolve("projects", id ?? "");
if (!id || !name || !/^[a-zA-Z0-9._-]{3,100}$/.test(id)) {
  console.error("Usage: node tools/create-plugin.mjs --id com.example.demo --name Demo [--author Name] [--out directory]");
  process.exit(2);
}
const toolsDirectory = dirname(fileURLToPath(import.meta.url));
const sdkDirectory = dirname(toolsDirectory);
await mkdir(output, { recursive: false });
await mkdir(join(output, "src"));
await cp(toolsDirectory, join(output, "tools"), { recursive: true });
await cp(join(sdkDirectory, "types"), join(output, "types"), { recursive: true });
await writeFile(join(output, "manifest.json"), `${JSON.stringify({
  schemaVersion: 1, id, name, version: "1.0.0", author,
  description: `${name} plugin`, kind: "ui", entry: "dist/index.js",
  hostApi: ">=1.1.0 <2.0.0", surfaces: ["home"], slots: [],
  permissions: [], networkDomains: [], services: [],
}, null, 2)}\n`);
await writeFile(join(output, "src", "index.ts"), `/// <reference path="../types/abu-plugin.d.ts" />\n\nglobalThis.ABUPlugin = {\n  render({ surface }) {\n    return { ui: { type: "column", children: [\n      { type: "text", text: ${JSON.stringify(name)}, tone: "accent" },\n      { type: "text", text: \`Surface: \${surface}\` },\n      { type: "button", text: "Device info", action: "device" }\n    ] } };\n  },\n  onAction({ action }) {\n    if (action === "device") return { capabilities: [{ id: "device", capability: "device.info" }] };\n    return this.render({ surface: "home", route: "root" });\n  },\n  onCapabilities({ results }) {\n    return { ui: { type: "text", text: JSON.stringify(results[0]) } };\n  }\n};\n`);
await writeFile(join(output, "tsconfig.json"), `${JSON.stringify({ compilerOptions: {
  target: "ES2020", module: "none", strict: true, outFile: "dist/index.js", removeComments: true,
}, include: ["src/index.ts", "types/abu-plugin.d.ts"] }, null, 2)}\n`);
await writeFile(join(output, "package.json"), `${JSON.stringify({
  name: id, version: "1.0.0", private: true, type: "module",
  scripts: { build: "tsc -p . && node tools/pack.mjs .", validate: `node tools/validate.mjs dist/${id}-1.0.0.abu-plugin` },
  devDependencies: { typescript: "^5.9.2" },
}, null, 2)}\n`);
await writeFile(join(output, ".gitignore"), "node_modules/\ndist/\n.keys/\n");
await writeFile(join(output, "README.md"), `# ${name}\n\n1. Run \`npm install\`.\n2. Run \`npm run build\`.\n3. Run \`npm run validate\`.\n4. Import the .abu-plugin from dist/ in ABU Launcher settings.\n\nSee https://github.com/Mashiro000/ABU-launcher/tree/main/plugin-sdk for the full API.\n`);
console.log(`Created ${output}`);
