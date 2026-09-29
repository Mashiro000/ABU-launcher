import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { generateKeyPairSync, createPrivateKey, createPublicKey, sign } from "node:crypto";

const [packageArg, keyArg = ".release-secrets/abu-plugin-ed25519.pem"] = process.argv.slice(2);
if (!packageArg) {
  console.error("Usage: node tools/sign-plugin.mjs <package.abu-plugin> [private-key.pem]");
  process.exit(2);
}

const packagePath = resolve(packageArg);
const keyPath = resolve(keyArg);
mkdirSync(dirname(keyPath), { recursive: true });

if (!existsSync(keyPath)) {
  const { privateKey } = generateKeyPairSync("ed25519");
  writeFileSync(keyPath, privateKey.export({ type: "pkcs8", format: "pem" }), { mode: 0o600 });
  console.error(`Created signing key: ${keyPath}`);
}

const privateKey = createPrivateKey(readFileSync(keyPath));
const publicDer = createPublicKey(privateKey).export({ type: "spki", format: "der" });
const publicRaw = publicDer.subarray(publicDer.length - 32);
const signature = sign(null, readFileSync(packagePath), privateKey);

console.log(JSON.stringify({
  publicKey: publicRaw.toString("base64"),
  signature: signature.toString("base64"),
}, null, 2));
