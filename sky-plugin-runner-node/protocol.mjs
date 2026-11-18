import grpc from '@grpc/grpc-js';
import loader from '@grpc/proto-loader';
import { readFileSync } from 'node:fs';
const descriptor = JSON.parse(readFileSync(new URL('./generated/protocol.json', import.meta.url)));
export const protocol = grpc.loadPackageDefinition(loader.fromJSON(descriptor, {
  longs: String, enums: String, defaults: true, oneofs: true
})).sky.plugin.v1;
export { grpc };
