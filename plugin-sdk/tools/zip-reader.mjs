import { inflateRawSync } from "node:zlib";

// Reads the ZIP central directory. Packages are capped well below ZIP64 size.
export function readZip(buffer) {
  const tail = Math.max(0, buffer.length - 65557);
  let end = -1;
  for (let offset = buffer.length - 22; offset >= tail; offset--) {
    if (buffer.readUInt32LE(offset) === 0x06054b50) { end = offset; break; }
  }
  if (end < 0) throw new Error("package is not a ZIP archive");
  const count = buffer.readUInt16LE(end + 10);
  let cursor = buffer.readUInt32LE(end + 16);
  const centralSize = buffer.readUInt32LE(end + 12);
  if (cursor + centralSize > end) throw new Error("invalid ZIP central directory bounds");
  const entries = new Map();
  for (let index = 0; index < count; index++) {
    if (cursor + 46 > end) throw new Error("truncated ZIP directory");
    if (buffer.readUInt32LE(cursor) !== 0x02014b50) throw new Error("invalid ZIP directory");
    const method = buffer.readUInt16LE(cursor + 10);
    const compressed = buffer.readUInt32LE(cursor + 20);
    const uncompressed = buffer.readUInt32LE(cursor + 24);
    const nameLength = buffer.readUInt16LE(cursor + 28);
    const extraLength = buffer.readUInt16LE(cursor + 30);
    const commentLength = buffer.readUInt16LE(cursor + 32);
    const localOffset = buffer.readUInt32LE(cursor + 42);
    if (cursor + 46 + nameLength + extraLength + commentLength > end) throw new Error("truncated ZIP entry");
    const name = buffer.subarray(cursor + 46, cursor + 46 + nameLength).toString("utf8");
    if (entries.has(name)) throw new Error(`duplicate ZIP entry: ${name}`);
    entries.set(name, { method, compressed, uncompressed, localOffset });
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  return {
    entries,
    read(name, maxBytes = 2 * 1024 * 1024) {
      const entry = entries.get(name);
      if (!entry) throw new Error(`missing ZIP entry: ${name}`);
      if (entry.uncompressed > maxBytes) throw new Error(`${name}: file exceeds size limit`);
      const offset = entry.localOffset;
      if (offset + 30 > buffer.length) throw new Error(`${name}: invalid local offset`);
      if (buffer.readUInt32LE(offset) !== 0x04034b50) throw new Error(`${name}: invalid local header`);
      const start = offset + 30 + buffer.readUInt16LE(offset + 26) + buffer.readUInt16LE(offset + 28);
      if (start + entry.compressed > buffer.length) throw new Error(`${name}: truncated compressed data`);
      const data = buffer.subarray(start, start + entry.compressed);
      const result = entry.method === 0 ? data : entry.method === 8 ? inflateRawSync(data, { maxOutputLength: maxBytes }) : null;
      if (!result || result.length !== entry.uncompressed || result.length > maxBytes) throw new Error(`${name}: invalid compressed data`);
      return result;
    },
  };
}
