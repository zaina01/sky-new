import protobuf from 'protobufjs';
import { writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const root = await protobuf.load(fileURLToPath(new URL('../sky-plugin-protocol/src/main/proto/sky_plugin.proto', import.meta.url)));
await mkdir(new URL('./generated/', import.meta.url), { recursive: true });
await writeFile(new URL('./generated/protocol.json', import.meta.url), JSON.stringify(root.toJSON()));
