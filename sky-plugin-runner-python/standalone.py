import json
import os
import re
import signal
import subprocess
import sys
import time

SYSTEM_NAMES = ('SystemRoot','WINDIR','PATH','Path','TEMP','TMP','TMPDIR','HOME','USERPROFILE','LANG','LC_ALL','SSL_CERT_FILE','SSL_CERT_DIR')
RESERVED = {name.upper() for name in SYSTEM_NAMES} | {'PATHEXT','COMSPEC','BASH_ENV','ENV'}


def child_environment(values):
    env = {key: os.environ[key] for key in SYSTEM_NAMES if key in os.environ}
    names = set()
    for key, value in values.items():
        upper = key.upper()
        if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]{0,127}',key) or upper.startswith(('SKY_','NODE_','PYTHON','LD_','DYLD_')) or upper in RESERVED or upper in names:
            raise ValueError('Invalid or reserved script environment name')
        if '\0' in value: raise ValueError('Invalid script environment value')
        names.add(upper)
        env[key] = value
    env.update(PYTHONUTF8='1', PYTHONUNBUFFERED='1', PYTHONNOUSERSITE='1')
    return env


def kill_tree(process):
    if os.name == 'nt':
        if process.poll() is None:
            try:
                subprocess.run([os.path.join(os.environ.get('SystemRoot',r'C:\Windows'),'System32','taskkill.exe'),'/PID',str(process.pid),'/T','/F'],stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=5,creationflags=subprocess.CREATE_NO_WINDOW)
            except (OSError, subprocess.TimeoutExpired): pass
    else:
        try: os.killpg(process.pid,signal.SIGKILL)
        except ProcessLookupError: pass
    if process.poll() is None: process.kill()
    process.wait(timeout=5)


def run_script(entry, options, cancelled, deadline):
    args = list(options.arguments)
    if len(args)>256 or len(options.environment)>128 or any('\0' in arg for arg in args): raise ValueError('Invalid script options')
    size = sum(len(arg.encode('utf-8'))+1 for arg in args) + sum(len(k.encode('utf-8'))+len(v.encode('utf-8'))+2 for k,v in options.environment.items())
    if size>24576: raise ValueError('Script options exceed 24 KiB')
    env = child_environment(options.environment)
    if sum(len(k)+len(v)+2 for k,v in env.items())>30000: raise ValueError('Script environment exceeds portable process limit')
    if cancelled.is_set() or time.monotonic()>=deadline: return {'exitCode':None,'signal':None}
    try:
        process = subprocess.Popen([sys.executable,'-u',str(entry),*args],env=env,stdin=subprocess.DEVNULL,start_new_session=os.name!='nt',creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    except OSError: raise RuntimeError('Unable to start script process') from None
    try:
        while process.poll() is None:
            if cancelled.wait(.025) or time.monotonic()>=deadline: return {'exitCode':None,'signal':None}
        code=process.returncode
        return {'exitCode':code if code>=0 else None,'signal':signal.Signals(-code).name if code<0 else None}
    finally: kill_tree(process)
