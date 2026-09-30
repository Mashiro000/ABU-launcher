import assert from "node:assert/strict";
import test from "node:test";
import { execFileSync } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { createHash, generateKeyPairSync, sign, verify } from "node:crypto";
import { validateManifest } from "./manifest.mjs";
import { validatePackage } from "./validate.mjs";
import { writeZip } from "./zip-writer.mjs";
import { safeDiagnosticLine } from "./logs.mjs";

const example = resolve("dist/com.example.hello-1.0.0.abu-plugin");

test("debug log command outputs phases without credentials", () => {
  const raw = "D/ABUPlugin: pluginId=com.example.a phase=network.fetch result=Rejected token=private https://host/path?key=private";
  assert.equal(safeDiagnosticLine(raw, "com.example.a"), "pluginId=com.example.a phase=network.fetch result=Rejected");
  assert.equal(safeDiagnosticLine(raw, "com.example.b"), null);
});

test("manifest validator rejects unsupported host declarations", () => {
  const base = { schemaVersion: 1, id: "com.example.app", version: "1.0.0", name: "App", author: "Dev", kind: "ui", entry: "dist/index.js" };
  assert.deepEqual(validateManifest(base), []);
  assert.match(validateManifest({ ...base, entry: "../evil.js" }).join(), /entry/);
  assert.match(validateManifest({ ...base, networkDomains: ["api.example.com"] }).join(), /network/);
  assert.match(validateManifest({ ...base, kind: "player" }).join(), /kind/);
});

test("packaged example is readable and reports its manifest", async () => {
  const result = await validatePackage(example);
  assert.equal(result.manifest.id, "com.example.hello");
  assert.ok(result.bytes > 0);
});

test("Ed25519 metadata verifies the exact package bytes", async () => {
  const bytes = await readFile(example);
  const { privateKey, publicKey } = generateKeyPairSync("ed25519");
  const signature = sign(null, bytes, privateKey);
  assert.ok(verify(null, bytes, publicKey, signature));
  assert.match(createHash("sha256").update(bytes).digest("hex"), /^[0-9a-f]{64}$/);
});

test("publish metadata validates and rejects a changed signature or hash", async () => {
  const root = await mkdtemp(join(tmpdir(), "abu-sign-test-"));
  try {
    const result = JSON.parse(execFileSync(process.execPath, [resolve("tools/publish.mjs"),
      "--file", example, "--key", join(root, "publisher.pem"),
      "--url", "https://example.com/plugin.abu-plugin"], { encoding: "utf8" }));
    const valid = [resolve("tools/validate.mjs"), example, "--sha256", result.asset.sha256,
      "--public-key", result.publicKey, "--signature", result.asset.signature];
    execFileSync(process.execPath, valid, { stdio: "pipe" });
    assert.throws(() => execFileSync(process.execPath, [...valid.slice(0, 3), "0".repeat(64), ...valid.slice(4)], { stdio: "pipe" }));
    const wrong = [...valid];
    wrong[wrong.indexOf("--signature") + 1] = Buffer.alloc(64).toString("base64");
    assert.throws(() => execFileSync(process.execPath, wrong, { stdio: "pipe" }));
  } finally { await rm(root, { recursive: true, force: true }); }
});

test("packaging the same source twice produces identical bytes", async () => {
  const pack = resolve("tools/pack.mjs");
  const source = resolve("examples/hello");
  execFileSync(process.execPath, [pack, source]);
  const first = await readFile(example);
  execFileSync(process.execPath, [pack, source]);
  assert.deepEqual(await readFile(example), first);
});

test("scaffold creates a standalone package and does not overwrite", async () => {
  const root = await mkdtemp(join(tmpdir(), "abu-sdk-test-"));
  const output = join(root, "sample");
  const tool = resolve("tools/create-plugin.mjs");
  try {
    execFileSync(process.execPath, [tool, "--id", "com.example.scaffold", "--name", "A \"quoted\" name", "--out", output]);
    const manifest = JSON.parse(await readFile(join(output, "manifest.json"), "utf8"));
    assert.equal(manifest.hostApi, ">=1.1.0 <2.0.0");
    assert.match(await readFile(join(output, "src/index.ts"), "utf8"), /A \\"quoted\\" name/);
    execFileSync(process.execPath, [resolve("node_modules/typescript/bin/tsc"), "-p", output]);
    execFileSync(process.execPath, [join(output, "tools/pack.mjs"), output], { cwd: output });
    assert.equal((await validatePackage(join(output, "dist/com.example.scaffold-1.0.0.abu-plugin"))).manifest.id, "com.example.scaffold");
    execFileSync(process.execPath, [join(output, "tools/validate-project.mjs"), output], { cwd: output });
    manifest.version = "1.0.1";
    await writeFile(join(output, "manifest.json"), JSON.stringify(manifest));
    execFileSync(process.execPath, [join(output, "tools/pack.mjs"), output], { cwd: output });
    execFileSync(process.execPath, [join(output, "tools/validate-project.mjs"), output], { cwd: output });
    assert.throws(() => execFileSync(process.execPath, [tool, "--id", "com.example.scaffold", "--name", "Demo", "--out", output], { stdio: "pipe" }));
  } finally { await rm(root, { recursive: true, force: true }); }
});

test("validator rejects traversal, duplicate ZIP entries and oversized declarations", async () => {
  const root = await mkdtemp(join(tmpdir(), "abu-zip-test-"));
  const path = join(root, "bad.abu-plugin");
  const manifest = Buffer.from(JSON.stringify({
    schemaVersion: 1, id: "com.example.bad", version: "1.0.0", name: "Bad", author: "Dev",
    kind: "ui", entry: "dist/index.js", hostApi: ">=1.1.0 <2.0.0",
  }));
  const entry = Buffer.from("globalThis.ABUPlugin = { render() { return {}; } };");
  try {
    await writeFile(path, writeZip([["manifest.json", manifest], ["dist/index.js", entry], ["../escape", Buffer.from("x")]]));
    await assert.rejects(validatePackage(path), /unsafe ZIP path/);
    await writeFile(path, writeZip([["manifest.json", manifest], ["manifest.json", manifest], ["dist/index.js", entry]]));
    await assert.rejects(validatePackage(path), /duplicate ZIP entry/);
    await writeFile(path, writeZip([["manifest.json", manifest], ["dist/index.js", Buffer.alloc(2 * 1024 * 1024 + 1)]]));
    await assert.rejects(validatePackage(path), /file exceeds size limit/);
  } finally { await rm(root, { recursive: true, force: true }); }
});
