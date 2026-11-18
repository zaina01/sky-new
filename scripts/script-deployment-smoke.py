"""Exercise native plugins in the disposable deployment created by docker-smoke.sh.

Reuses that deployment's temporary administrator and state; never creates users or
connects to a database directly. Run initialize after the Java smoke initialization,
then verify after recreating the backend with the same data volume and encryption key.
"""
import argparse
from datetime import datetime
import io
import json
from pathlib import Path
import time
import urllib.error
import urllib.request
import uuid
import zipfile


class ScriptSmoke:
    def __init__(self, base, state):
        self.base = base.rstrip("/")
        self.token = ""
        self.token = self.request("/api/user/login", "POST", {
            "username": state["username"], "password": state["password"],
        })["token"]

    def request(self, path, method="GET", value=None, raw=None, content_type=None):
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if value is not None:
            raw = json.dumps(value, ensure_ascii=False).encode("utf-8")
            content_type = "application/json"
        if content_type:
            headers["Content-Type"] = content_type
        req = urllib.request.Request(self.base + path, raw, headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=180) as response:
                result = json.load(response)
        except urllib.error.HTTPError as error:
            raise AssertionError(f"{method} {path}: HTTP {error.code}") from error
        assert result["code"] == 0, (method, path, result.get("msg"))
        return result["data"]

    def upload(self, filename, code):
        boundary = uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                'Content-Type: application/octet-stream\r\n\r\n').encode()
        body += code if isinstance(code, bytes) else code.encode("utf-8")
        body += f"\r\n--{boundary}--\r\n".encode()
        return self.request("/api/plugins/artifacts", "POST", raw=body,
                            content_type=f"multipart/form-data; boundary={boundary}")

    def create_job(self, installation):
        return self.request("/api/jobs", "POST", {
            "jobName": "Native container smoke", "pluginInstallationId": installation,
            "cronExpression": "0 0 0 1 1 ? 2099", "misfirePolicy": "DO_NOTHING",
            "concurrent": False, "inputJson": "{}", "timeoutMillis": 30000,
        })["id"]

    def run(self, job, engine, mode, expected="SUCCESS"):
        execution = self.request(f"/api/jobs/{job}/run", "POST", {})["executionId"]
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            row = self.request(f"/api/job-executions/{execution}")
            if row["status"] != "RUNNING":
                break
            time.sleep(.2)
        assert row["status"] == expected, (row["status"], row.get("errorMessage"), row.get("message"))
        assert row["runtimeEngine"] == engine and row["executionMode"] == mode
        assert row["environmentRevision"] and row["artifactSha256"] and row["interpreterVersion"]
        elapsed = (datetime.fromisoformat(row["finishedAt"]) - datetime.fromisoformat(row["startedAt"])).total_seconds() * 1000
        assert abs(elapsed - row["durationMillis"]) < 5000, "Host/database time zones disagree"
        return row

    def logs(self, execution):
        return self.request(f"/api/job-executions/{execution}/logs?tailBytes=65536")["content"]

    def initialize_environment(self, engine):
        base = "/api/plugins/script-environments/" + engine
        health = self.request(base + "/health")
        assert health["available"], (engine, health.get("message"))
        assert not health["initialized"], "Smoke requires a fresh deployment"
        operation = self.request(base + "/operations", "POST", {"action": "initialize"})
        deadline = time.monotonic() + 600
        while time.monotonic() < deadline:
            operation = self.request("/api/plugins/script-environments/operations/" + operation["id"])
            if operation["status"] in ("SUCCEEDED", "FAILED"):
                break
            time.sleep(.5)
        if operation["status"] != "SUCCEEDED":
            log = self.request("/api/plugins/script-environments/operations/" + operation["id"] + "/log")
            print(log["content"][-16000:], flush=True)
            raise AssertionError((engine, operation.get("error")))
        environment = self.request(base)
        version = environment["interpreterVersion"]
        expected_version = "v24." if engine == "node" else "Python 3.12."
        assert version.startswith(expected_version), version
        print(f"Initialized {engine}: {version}", flush=True)
        return environment["revision"]

    def sdk(self, engine):
        plugin_id = "container-smoke-" + engine
        entry = "main.mjs" if engine == "node" else "main.py"
        manifest = {
            "formatVersion": 1, "pluginId": plugin_id, "version": "1.0.0", "displayName": plugin_id,
            "runtime": {"engine": engine, "entry": entry},
            "capabilities": {"task": True, "config": True, "account": True, "notification": False, "service": False},
            "configSchema": {"fields": []}, "accountSchema": {"fields": []},
        }
        code = '''export async function run(ctx) {
 const config = await ctx.config.get(), accounts = await ctx.accounts.list();
 if (config.marker !== 'native smoke' || accounts.length !== 1) throw new Error('Callback inputs');
 await ctx.data.put('smoke','entry',{marker:config.marker});
 if ((await ctx.data.get('smoke','entry')).marker !== config.marker) throw new Error('Data callback');
 await ctx.data.delete('smoke','entry');
 console.log('SDK中文 callbacks verified');
 return ctx.success('SDK smoke passed',{processed:accounts.length});
}
''' if engine == "node" else '''def run(ctx):
    config, accounts = ctx.config.get(), ctx.accounts.list()
    assert config['marker'] == 'native smoke' and len(accounts) == 1
    ctx.data.put('smoke','entry',{'marker':config['marker']})
    assert ctx.data.get('smoke','entry')['marker'] == config['marker']
    ctx.data.delete('smoke','entry')
    print('SDK中文 callbacks verified',flush=True)
    return ctx.success('SDK smoke passed',{'processed':len(accounts)})
'''
        content = io.BytesIO()
        with zipfile.ZipFile(content, "w") as archive:
            archive.writestr("sky-script.json", json.dumps(manifest))
            archive.writestr(entry, code)
        self.upload(plugin_id + ".zip", content.getvalue())
        installation = self.request("/api/plugins/installations", "POST", {
            "pluginId": plugin_id, "installationName": plugin_id,
        })["installationId"]
        base = "/api/plugins/installations/" + installation
        self.request(base + "/config", "PUT", {"configJson": '{"marker":"native smoke"}'})
        for account, enabled in (("enabled", True), ("disabled", False)):
            self.request(base + "/accounts", "POST", {
                "accountId": account, "displayName": account, "accountJson": "{}", "enabled": enabled,
            })
        job = self.create_job(installation)
        row = self.run(job, engine, "plugin")
        assert json.loads(row["resultJson"])["processed"] == 1
        assert "SDK中文 callbacks verified" in self.logs(row["id"])
        print(f"Passed {engine} ZIP SDK callbacks and logs", flush=True)
        return {"job": job, "engine": engine, "mode": "plugin", "execution": row["id"]}

    def standalone(self, extension):
        engine = "python" if extension == "py" else "node"
        code = '''import json,os,sys
print('SNAPSHOT:'+json.dumps({'account':os.getenv('SMOKE_ACCOUNT'),'args':sys.argv[1:],'leaks':[k for k in os.environ if k.startswith('SKY_')]},ensure_ascii=False),flush=True)
if '--fail' in sys.argv: sys.exit(9)
''' if engine == "python" else '''console.log('SNAPSHOT:'+JSON.stringify({account:process.env.SMOKE_ACCOUNT,args:process.argv.slice(2),leaks:Object.keys(process.env).filter(k=>k.startsWith('SKY_'))}));
if (process.argv.includes('--fail')) process.exitCode=9;
'''
        plugin = self.upload("smoke." + extension, code)
        installation = self.request("/api/plugins/installations", "POST", {
            "pluginId": plugin["pluginId"], "installationName": "Single " + extension,
        })["installationId"]
        base = "/api/plugins/installations/" + installation
        for value in ("账号A", "账号B"):
            variable = self.request(base + "/script-environment-variables", "POST", {
                "name": "SMOKE_ACCOUNT", "value": value, "sensitive": True, "enabled": True,
            })
            assert "value" not in variable and "encryptedValue" not in variable
        self.request(base + "/script-environment-settings", "PUT", {"defaultSeparator": "@@", "overrides": []})
        arguments = ["", "中文 空格", "line1\nline2", "$(literal)"]
        self.request(base + "/script-arguments", "PUT", {"arguments": ["--fail"]})
        job = self.create_job(installation)
        failed = self.run(job, engine, "standalone", "FAILED")
        assert json.loads(failed["resultJson"])["exitCode"] == 9
        options = self.request(base + "/script-arguments", "PUT", {"arguments": arguments})
        row = self.run(job, engine, "standalone")
        fixture = {"job": job, "engine": engine, "mode": "standalone", "installation": installation,
                   "revision": options["revision"], "arguments": arguments, "execution": row["id"]}
        self.verify_standalone(row, fixture)
        print(f"Passed .{extension}: encrypted variables, merge, arguments and exit codes", flush=True)
        return fixture

    def verify_standalone(self, row, fixture):
        assert row["scriptOptionsRevision"] == fixture["revision"]
        assert json.loads(row["resultJson"])["exitCode"] == 0
        line = next(line[9:] for line in self.logs(row["id"]).splitlines() if line.startswith("SNAPSHOT:"))
        assert json.loads(line) == {"account": "账号A@@账号B", "args": fixture["arguments"], "leaks": []}

    def verify(self, smoke):
        for engine, revision in smoke["environments"].items():
            assert self.request("/api/plugins/script-environments/" + engine)["revision"] == revision
            assert self.request(f"/api/plugins/script-environments/{engine}/health")["available"]
        for fixture in smoke["fixtures"]:
            assert self.request(f'/api/job-executions/{fixture["execution"]}')["status"] == "SUCCESS"
            assert self.logs(fixture["execution"])
            row = self.run(fixture["job"], fixture["engine"], fixture["mode"])
            assert row["environmentRevision"] == smoke["environments"][fixture["engine"]]
            if fixture["mode"] == "standalone":
                options = self.request(f'/api/plugins/installations/{fixture["installation"]}/script-arguments')
                assert options["revision"] == fixture["revision"] and options["arguments"] == fixture["arguments"]
                self.verify_standalone(row, fixture)
            else:
                assert "SDK中文 callbacks verified" in self.logs(row["id"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base")
    parser.add_argument("state", type=Path)
    parser.add_argument("phase", choices=["initialize", "verify"])
    args = parser.parse_args()
    state = json.loads(args.state.read_text(encoding="utf-8"))
    client = ScriptSmoke(args.base, state)
    if args.phase == "initialize":
        assert "scriptSmoke" not in state, "Smoke already initialized"
        smoke = {"environments": {engine: client.initialize_environment(engine) for engine in ("node", "python")}}
        smoke["fixtures"] = [client.sdk(engine) for engine in ("node", "python")]
        smoke["fixtures"] += [client.standalone(extension) for extension in ("js", "mjs", "py")]
        state["scriptSmoke"] = smoke
        args.state.write_text(json.dumps(state), encoding="utf-8")
        args.state.chmod(0o600)
    else:
        client.verify(state["scriptSmoke"])
    print(f"Native deployment {args.phase} passed: Node/Python SDK, .js/.mjs/.py and persistent encrypted options.", flush=True)


if __name__ == "__main__":
    main()
