import { spawn } from "node:child_process";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

export function safeDiagnosticLine(line, id) {
  const match = /\bpluginId=([A-Za-z0-9._-]+) phase=([A-Za-z0-9_.:-]+) result=([A-Za-z0-9_-]+)\b/.exec(line);
  return match?.[1] === id ? `pluginId=${match[1]} phase=${match[2]} result=${match[3]}` : null;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const args = process.argv.slice(2);
  const value = (flag) => args.includes(flag) ? args[args.indexOf(flag) + 1] : null;
  const id = value("--id");
  const serial = value("--serial");
  const adb = value("--adb") || "adb";
  if (!id || !/^[A-Za-z0-9._-]{3,100}$/.test(id)) {
    console.error("Usage: node tools/logs.mjs --id com.example.demo [--serial device] [--adb path-to-adb]");
    process.exit(2);
  }
  const adbArgs = [...(serial ? ["-s", serial] : []), "logcat", "-v", "brief", "-s", "ABUPlugin:D", "*:S"];
  const child = spawn(adb, adbArgs, { stdio: ["inherit", "pipe", "inherit"] });
  let buffer = "";
  child.stdout.setEncoding("utf8");
  child.stdout.on("data", (chunk) => {
    buffer += chunk;
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() || "";
    for (const line of lines) {
      const safe = safeDiagnosticLine(line, id);
      if (safe) console.log(safe);
    }
  });
  child.on("error", (error) => { console.error(`Could not start adb: ${error.message}`); process.exitCode = 1; });
  child.on("exit", (code) => { if (code) process.exitCode = code; });
}
