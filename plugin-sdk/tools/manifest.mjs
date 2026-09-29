import { posix } from "node:path";

export const MAX_ENTRY_BYTES = 2 * 1024 * 1024;
export const MAX_PACKAGE_BYTES = 250 * 1024 * 1024;
export const SCRIPT_KINDS = new Set(["ui", "data_source", "subtitle", "system"]);
export const SURFACES = new Set(["home", "settings", "player"]);
export const SLOTS = new Set(["home.quickActions", "player.overlay"]);
export const PERMISSIONS = new Set(["storage", "network", "usb", "bluetooth"]);

export function validRelativePath(value) {
  return typeof value === "string" && value.length > 0 && !value.startsWith("/") &&
    !value.includes("\\") && !value.includes(":") && !value.includes("\0") &&
    value.split("/").every((segment) => segment.length > 0 && segment !== "." && segment !== "..") &&
    posix.normalize(value) === value;
}

export function validateManifest(manifest) {
  const errors = [];
  const add = (field, message) => errors.push(`${field}: ${message}`);
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) return ["manifest: expected JSON object"];
  if (manifest.schemaVersion !== 1) add("schemaVersion", "must be 1");
  if (!/^[a-zA-Z0-9._-]{3,100}$/.test(manifest.id ?? "")) add("id", "3–100 letters, digits, dots, dashes or underscores");
  if (!/^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$/.test(manifest.version ?? "")) add("version", "invalid version");
  for (const field of ["name", "author"]) if (typeof manifest[field] !== "string" || !manifest[field].trim()) add(field, "required non-empty string");
  if (!SCRIPT_KINDS.has(manifest.kind)) add("kind", "script plugins must use ui, data_source, subtitle or system");
  if (!validRelativePath(manifest.entry) || !manifest.entry.endsWith(".js")) add("entry", "must be a relative .js path inside the package");
  if (manifest.hostApi !== undefined) {
    const match = /^>=(\d+)\.(\d+)\.(\d+) <(\d+)\.(\d+)\.(\d+)$/.exec(manifest.hostApi);
    if (!match) add("hostApi", "use >=major.minor.patch <major.minor.patch");
    else {
      const lower = match.slice(1, 4).map(Number);
      const upper = match.slice(4, 7).map(Number);
      if (lower.every((part, index) => part === upper[index]) ||
        lower.find((part, index) => part !== upper[index]) > upper.find((part, index) => part !== lower[index])) add("hostApi", "lower bound must be below upper bound");
    }
  }
  for (const field of ["surfaces", "slots", "networkDomains", "permissions", "services"]) {
    if (manifest[field] !== undefined && !Array.isArray(manifest[field])) add(field, "must be an array");
  }
  for (const surface of Array.isArray(manifest.surfaces) ? manifest.surfaces : []) if (!SURFACES.has(surface)) add("surfaces", `unsupported value ${surface}`);
  for (const slot of Array.isArray(manifest.slots) ? manifest.slots : []) if (!SLOTS.has(slot)) add("slots", `unsupported value ${slot}`);
  for (const [index, permission] of (Array.isArray(manifest.permissions) ? manifest.permissions : []).entries()) {
    if (!PERMISSIONS.has(permission?.id) || !permission?.title) add(`permissions[${index}]`, "requires a supported id and title");
  }
  for (const [index, service] of (Array.isArray(manifest.services) ? manifest.services : []).entries()) {
    if (!/^[a-zA-Z0-9._-]{1,100}$/.test(service?.name ?? "") || !Number.isInteger(service?.version) || service.version < 1) {
      add(`services[${index}]`, "requires a valid name and positive integer version");
    }
  }
  if (Array.isArray(manifest.services) && new Set(manifest.services.map((service) => service?.name)).size !== manifest.services.length) add("services", "service names must be unique");
  for (const [index, domain] of (Array.isArray(manifest.networkDomains) ? manifest.networkDomains : []).entries()) {
    if (typeof domain !== "string" || !/^[a-z0-9.-]+$/i.test(domain) || domain.startsWith(".") || domain.includes("..")) add(`networkDomains[${index}]`, "use a hostname without protocol or path");
  }
  if (Array.isArray(manifest.networkDomains) && manifest.networkDomains.length && !(Array.isArray(manifest.permissions) && manifest.permissions.some((item) => item?.id === "network"))) add("networkDomains", "also declare network permission");
  return errors;
}
