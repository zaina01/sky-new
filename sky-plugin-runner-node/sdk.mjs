import { grpc } from './protocol.mjs';

const marker = Symbol('SkyTaskResult');
export class PluginError extends Error {
  constructor(message, code = 'PLATFORM_ERROR') { super(message); this.name = code; }
}
function encode(value) {
  const result = JSON.stringify(value, (_, item) => {
    if (typeof item === 'number' && !Number.isFinite(item)) throw new TypeError('Non-finite JSON number');
    if (['undefined', 'function', 'symbol', 'bigint'].includes(typeof item)) throw new TypeError('Non-JSON value');
    return item;
  });
  if (result === undefined) throw new TypeError('Non-JSON value');
  return result;
}
const decode = value => value ? JSON.parse(value) : null;
export function resultValue(value) {
  if (!value || value[marker] !== true) throw new TypeError('run(ctx) must return ctx.success() or ctx.failure()');
  return { succeeded: value.succeeded, message: value.message, resultJson: encode(value.data) };
}

export function createContext(request, env, client, deadline, signal) {
  const metadata = new grpc.Metadata();
  metadata.set('authorization', `Bearer ${env.SKY_PLUGIN_TOKEN}`);
  metadata.set('x-sky-plugin-id', env.SKY_PLUGIN_ID);
  metadata.set('x-sky-plugin-instance-id', env.SKY_PLUGIN_INSTANCE_ID);
  const identity = { pluginId: env.SKY_PLUGIN_ID, instanceId: env.SKY_PLUGIN_INSTANCE_ID, jobId: request.jobId, requestId: request.requestId };
  const rpc = (method, params = {}) => new Promise((resolve, reject) => {
    if (signal.aborted) return reject(new PluginError('Task cancelled', 'CANCELLED'));
    const call = client[method]({ ...params, ...identity }, metadata, { deadline }, (error, response) => {
      signal.removeEventListener('abort', cancel);
      if (error) return reject(new PluginError(error.details || error.message, `GRPC_${error.code}`));
      if (response.success === false) return reject(new PluginError(response.errorMessage || response.message || 'Platform request rejected', response.errorType || 'PLATFORM_ERROR'));
      resolve(response);
    });
    const cancel = () => call.cancel();
    signal.addEventListener('abort', cancel, { once: true });
  });
  const account = row => ({ accountId: row.accountId, displayName: row.displayName, data: decode(row.accountJson) });
  return Object.freeze({
    pluginId: identity.pluginId, installationId: env.SKY_PLUGIN_INSTALLATION_ID,
    jobId: request.jobId, requestId: request.requestId, input: decode(request.inputJson), signal,
    config: Object.freeze({
      get: async () => decode((await rpc('getConfig')).configJson),
      update: async value => decode((await rpc('updateConfig', { configJson: encode(value) })).configJson)
    }),
    accounts: Object.freeze({
      list: async () => (await rpc('listAccounts')).accounts.filter(row => row.enabled).map(account),
      get: async id => (await rpc('getAccount', { accountId: String(id) })).accounts.map(account)[0] ?? null,
      update: async (id, value) => (await rpc('updateAccount', { accountId: String(id), accountJson: encode(value) })).accounts.map(account)[0] ?? null
    }),
    data: Object.freeze({
      get: async (collection, key) => decode((await rpc('getData', { collection, key })).dataJson),
      put: async (collection, key, value) => (await rpc('putData', { collection, key, dataJson: encode(value) })).success,
      delete: async (collection, key) => (await rpc('deleteData', { collection, key })).deleted,
      list: async collection => (await rpc('listData', { collection })).records.map(row => ({ key: row.key, data: decode(row.dataJson) }))
    }),
    notifications: Object.freeze({
      listChannels: async () => (await rpc('listNotificationChannels')).channels,
      send: async ({ channelIds = [], title = '', content = '', severity = 'INFO', labels = {} } = {}) =>
        rpc('sendNotification', { channelIds, title, content, severity, labels })
    }),
    services: Object.freeze({
      callInstallation: async (id, operation, data) => decode((await rpc('callPluginService', {
        callerPluginId: identity.pluginId, targetInstallationId: String(id), operation, requestJson: encode(data), timeoutMillis: String(Math.max(1, deadline - Date.now()))
      })).resultJson)
    }),
    log: Object.freeze(Object.fromEntries(['debug', 'info', 'warn', 'error'].map(level => [level, (...args) => console[level](...args)]))),
    success: (message = '', data = null) => ({ [marker]: true, succeeded: true, message: String(message), data }),
    failure: (message = '') => ({ [marker]: true, succeeded: false, message: String(message), data: null })
  });
}
