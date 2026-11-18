import { grpc, protocol } from './protocol.mjs';
import { createContext, resultValue } from './sdk.mjs';
import { pathToFileURL } from 'node:url';
import { timingSafeEqual } from 'node:crypto';
import { runStandalone } from './standalone.mjs';

if (process.argv[2] === '--check') {
  console.log('Node Runner protocol and SDK ready');
  process.exit(0);
}
const env = process.env;
for (const key of ['SKY_PLUGIN_ID', 'SKY_PLUGIN_INSTALLATION_ID', 'SKY_PLUGIN_INSTANCE_ID', 'SKY_PLUGIN_TOKEN', 'SKY_PLUGIN_PORT', 'SKY_HOST_GRPC_PORT']) {
  if (!env[key]) throw new Error(`Missing ${key}`);
}
const limit = Number(env.SKY_PLUGIN_GRPC_MAX_INBOUND_MESSAGE_BYTES || 1048576);
const options = { 'grpc.max_receive_message_length': limit, 'grpc.max_send_message_length': limit, 'grpc.enable_retries': 0 };
const host = new protocol.PluginHostService(`${env.SKY_HOST_GRPC_HOST || '127.0.0.1'}:${env.SKY_HOST_GRPC_PORT}`, grpc.credentials.createInsecure(), options);
const server = new grpc.Server(options);
let active = null, used = false;
const secureEqual = (a, b) => {
  const left = Buffer.from(a || ''), right = Buffer.from(b || '');
  return left.length === right.length && timingSafeEqual(left, right);
};
function authenticated(handler) {
  return (call, callback) => {
    const header = key => String(call.metadata.get(key)[0] || '');
    if (!secureEqual(header('authorization'), `Bearer ${env.SKY_PLUGIN_TOKEN}`)) return callback({ code: grpc.status.UNAUTHENTICATED, details: 'Invalid runtime credentials' });
    if (header('x-sky-plugin-id') !== env.SKY_PLUGIN_ID || header('x-sky-plugin-instance-id') !== env.SKY_PLUGIN_INSTANCE_ID)
      return callback({ code: grpc.status.PERMISSION_DENIED, details: 'Plugin identity mismatch' });
    if (call.request.pluginId && call.request.pluginId !== env.SKY_PLUGIN_ID) return callback({ code: grpc.status.PERMISSION_DENIED, details: 'Request identity mismatch' });
    if (call.request.instanceId && call.request.instanceId !== env.SKY_PLUGIN_INSTANCE_ID) return callback({ code: grpc.status.PERMISSION_DENIED, details: 'Request instance mismatch' });
    Promise.resolve().then(() => handler(call, callback)).catch(error => callback({ code: grpc.status.INTERNAL, details: error.message }));
  };
}
server.addService(protocol.PluginRunnerService.service, {
  health: authenticated((call, done) => done(null, { healthy: true, message: 'Node Runner ready' })),
  startPlugin: authenticated((call, done) => done(null, { started: true, message: 'Task Runner ready' })),
  callService: authenticated((call, done) => done({ code: grpc.status.UNIMPLEMENTED, details: 'Script service plugins are not supported' })),
  runTask: authenticated(async (call, done) => {
    const standalone=env.SKY_PLUGIN_EXECUTION_MODE === 'standalone';
    if(standalone !== (call.request.standaloneOptions != null)) return done({code:grpc.status.INVALID_ARGUMENT,details:'Task options do not match Runner execution mode'});
    if (used) return done({ code: grpc.status.FAILED_PRECONDITION, details: 'This Runner already accepted a task' });
    used = true;
    const request = call.request, startedAt = Date.now(), controller = new AbortController();
    const maximum = Number(env.SKY_PLUGIN_MAX_TASK_TIMEOUT_MILLIS || 120000);
    const requested = Number(request.timeoutMillis);
    const deadline = Math.min(Number(call.getDeadline()), startedAt + (requested > 0 ? Math.min(requested, maximum) : maximum));
    let timer;
    let finished = false;
    const finish = response => {
      if (finished) return;
      finished = true; clearTimeout(timer); active = null;
      done(null, { requestId: request.requestId, startedAtEpochMillis: String(startedAt), finishedAtEpochMillis: String(Date.now()), ...response });
    };
    active = { requestId: request.requestId, cancel: () => { controller.abort(); finish({ status: 'TASK_RUN_STATUS_CANCELLED', message: 'Task cancelled' }); } };
    call.on('cancelled', () => controller.abort());
    timer = setTimeout(() => { controller.abort(); finish({ status: 'TASK_RUN_STATUS_TIMEOUT', message: 'Task timed out' }); }, Math.max(1, deadline - Date.now()));
    try {
      if(standalone) {
        finish(await runStandalone(process.argv[2],request.standaloneOptions,controller.signal));
        return;
      }
      const entry = await import(pathToFileURL(process.argv[2]).href);
      if (typeof entry.run !== 'function') throw new TypeError('Entry must export run(ctx)');
      const context = createContext(request, env, host, deadline, controller.signal);
      const value = resultValue(await entry.run(context));
      finish({ status: value.succeeded ? 'TASK_RUN_STATUS_SUCCESS' : 'TASK_RUN_STATUS_FAILED', message: value.message, resultJson: value.resultJson });
    } catch (error) {
      console.error(error.stack || error);
      finish({ status: 'TASK_RUN_STATUS_FAILED', message: 'Plugin task failed', errorType: error.name, errorMessage: error.message });
    }
  }),
  cancelTask: authenticated((call, done) => {
    const cancelled = active?.requestId === call.request.requestId;
    if (cancelled) active.cancel();
    done(null, { cancelled, message: cancelled ? 'Cancellation requested' : 'Task not running' });
  }),
  stopPlugin: authenticated((call, done) => { active?.cancel(); done(null, { stopped: true }); setTimeout(() => { host.close(); server.forceShutdown(); }, 50); })
});
server.bindAsync(`${env.SKY_PLUGIN_GRPC_BIND_HOST || '127.0.0.1'}:${env.SKY_PLUGIN_PORT}`, grpc.ServerCredentials.createInsecure(), error => {
  if (error) { console.error(error); process.exitCode = 1; host.close(); }
});
