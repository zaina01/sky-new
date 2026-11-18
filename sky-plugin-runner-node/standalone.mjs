import { spawn } from 'node:child_process';
import { join } from 'node:path';

const systemNames = ['SystemRoot','WINDIR','PATH','Path','TEMP','TMP','TMPDIR','HOME','USERPROFILE','LANG','LC_ALL','SSL_CERT_FILE','SSL_CERT_DIR'];
const reserved = new Set([...systemNames.map(v=>v.toUpperCase()),'PATHEXT','COMSPEC','BASH_ENV','ENV']);
export function childEnvironment(values = {}) {
  const env = {};
  for (const key of systemNames) if (process.env[key] !== undefined) env[key] = process.env[key];
  const names = new Set();
  for (const [key,value] of Object.entries(values)) {
    const upper=key.toUpperCase();
    if (!/^[A-Za-z_][A-Za-z0-9_]{0,127}$/.test(key) || /^(SKY_|NODE_|PYTHON|LD_|DYLD_)/.test(upper) || reserved.has(upper) || names.has(upper)) throw Error('Invalid or reserved script environment name');
    if (typeof value !== 'string' || value.includes('\0')) throw Error('Invalid script environment value');
    names.add(upper);env[key]=value;
  }
  return env;
}
async function killTree(child) {
  if (!child.pid) return;
  if (process.platform === 'win32') {
    if (child.exitCode === null) await new Promise(resolve=>{
      const killer=spawn(join(process.env.SystemRoot || 'C:\\Windows','System32','taskkill.exe'),['/PID',String(child.pid),'/T','/F'],{stdio:'ignore',windowsHide:true});
      const timer=setTimeout(()=>{killer.kill();resolve();},5000);
      killer.once('error',()=>{clearTimeout(timer);resolve();});killer.once('exit',()=>{clearTimeout(timer);resolve();});
    });
  } else {
    try { process.kill(-child.pid,'SIGKILL'); } catch(error) { if(error.code!=='ESRCH') throw error; }
  }
  if(child.exitCode===null)child.kill('SIGKILL');
}
export async function runStandalone(entry, options, signal) {
  const args=options.arguments || [], env=childEnvironment(options.environment);
  if(args.length>256 || Object.keys(options.environment||{}).length>128 || args.some(a=>typeof a!=='string'||a.includes('\0')))throw Error('Invalid script arguments');
  if(Buffer.byteLength(args.join('\0'))+Object.entries(options.environment||{}).reduce((n,[k,v])=>n+Buffer.byteLength(k)+Buffer.byteLength(v)+2,0)>24576)throw Error('Script options exceed 24 KiB');
  if(Object.entries(env).reduce((n,[k,v])=>n+k.length+v.length+2,0)>30000)throw Error('Script environment exceeds portable process limit');
  if(signal.aborted)throw Error('Task cancelled before script launch');
  const child=spawn(process.execPath,[entry,...args],{env,stdio:['ignore','inherit','inherit'],detached:process.platform!=='win32',windowsHide:true,shell:false});
  let cleanup;
  const stop=()=>cleanup ||= killTree(child);
  const abort=()=>{void stop().catch(()=>child.kill('SIGKILL'));};
  signal.addEventListener('abort',abort,{once:true});
  try {
    const outcome=await new Promise((resolve,reject)=>{
      child.once('error',()=>reject(Error('Unable to start script process')));
      child.once('exit',(code,signal)=>resolve({exitCode:code,signal}));
    });
    return {status:outcome.exitCode===0?'TASK_RUN_STATUS_SUCCESS':'TASK_RUN_STATUS_FAILED',message:`Script exited with code ${outcome.exitCode ?? outcome.signal}`,resultJson:JSON.stringify(outcome),errorType:outcome.exitCode===0?'':'SCRIPT_EXIT'};
  } finally {signal.removeEventListener('abort',abort);await stop();}
}
