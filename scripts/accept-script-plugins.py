"""Real HTTP/MySQL acceptance for a NEW disposable deployment.

MYSQL_COMMAND is a JSON argument array for mysql (or docker exec -i ... mysql).
MYSQL_PWD supplies its credential; credentials are never written to reports.
Database mode refuses existing databases and names outside sky_script_acceptance_*.
The HTTP initialize phase refuses an already initialized deployment.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import io
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import zipfile


ROOT = Path(__file__).resolve().parents[1]


class Acceptance:
    def __init__(self, args):
        self.args = args
        assert re.fullmatch(r"sky_script_acceptance_[a-z0-9_]{1,32}", args.database), "Disposable database name required"
        self.token = ""
        self.report = {"startedAt": datetime.now(timezone.utc).isoformat(), "database": args.database,
                       "baseUrl": args.base_url, "phase": args.phase, "checks": []}
        if args.resume and args.report.exists():
            previous = json.loads(args.report.read_text(encoding="utf-8"))
            assert previous["database"] == args.database and previous["phase"] == args.phase
            self.report = previous

    def check(self, name, action):
        if self.args.resume and any(c["name"] == name and c["passed"] for c in self.report["checks"]):
            print("ALREADY PASSED:", name, flush=True)
            return
        start = time.monotonic()
        try:
            action()
        except Exception as error:
            self.report["checks"].append({"name": name, "passed": False, "error": str(error)})
            self.save_report()
            raise
        previous_failures = [c for c in self.report["checks"] if c["name"] == name and not c["passed"]]
        if previous_failures:
            self.report.setdefault("resolvedFailures", []).extend(previous_failures)
            self.report["checks"] = [c for c in self.report["checks"] if c not in previous_failures]
        self.report["checks"].append({"name": name, "passed": True, "seconds": round(time.monotonic()-start, 2)})
        self.save_report()
        print("PASS:", name, flush=True)

    def save_report(self):
        self.args.report.parent.mkdir(parents=True, exist_ok=True)
        self.args.report.write_text(json.dumps(self.report, ensure_ascii=False, indent=2), encoding="utf-8")

    def sql(self, sql, database=None):
        command = json.loads(os.environ["MYSQL_COMMAND"])
        assert isinstance(command, list) and all(isinstance(s, str) for s in command)
        result = subprocess.run(command + ["--batch", "--raw", "--skip-column-names", "--default-character-set=utf8mb4"],
                                input=(f"USE `{database or self.args.database}`;\n" + sql).encode("utf-8"),
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90)
        if result.returncode:
            raise AssertionError(result.stderr.decode("utf-8", "replace"))
        return result.stdout.decode("utf-8").strip()

    def prepare_database(self):
        name = self.args.database
        old = name + "_upgrade"
        for database in (name, old):
            # The metadata database is used only for these guards and CREATE DATABASE.
            count = self.sql(f"SELECT COUNT(*) FROM SCHEMATA WHERE SCHEMA_NAME='{database}';", "information_schema")
            assert count == "0", f"Refusing to overwrite existing database {database}"
        schema = (ROOT / "sql/schema-mysql.sql").read_text(encoding="utf-8")
        def create(database, text):
            text = text.replace("CREATE DATABASE IF NOT EXISTS sky_new", f"CREATE DATABASE `{database}`")
            text = text.replace("USE sky_new;", f"USE `{database}`;")
            self.sql(text, "information_schema")
        self.check("fresh schema on real MySQL", lambda: create(name, schema))
        baseline = self.args.baseline.read_text(encoding="utf-8")
        assert "runtime_engine" not in baseline, "Baseline must predate script plugins"
        create(old, baseline)
        self.sql("INSERT INTO plugin_definition(plugin_id,main_class,capabilities_json,config_schema_json,account_schema_json,dependency_jars_json) VALUES('upgrade-fixture','example.Legacy','{}','{}','{}','[]');", old)
        for filename in ("upgrade-script-plugins-mysql.sql", "upgrade-standalone-scripts-mysql.sql", "upgrade-instance-script-options-mysql.sql"):
            self.check(filename, lambda f=filename: self.sql((ROOT / "sql" / f).read_text(), old))
        self.sql("INSERT INTO installation_script_variable VALUES('preserved-id','migration-only','ACCOUNT',JSON_OBJECT('id','preserved-id','encryptedValue','preserved-ciphertext'),CURRENT_TIMESTAMP(3));", old)
        before = self.sql("SELECT variable_json FROM installation_script_variable WHERE variable_id='preserved-id';", old)
        def merge_upgrade():
            self.sql((ROOT / "sql/upgrade-script-environment-merge-mysql.sql").read_text(), old)
            assert self.sql("SELECT variable_json FROM installation_script_variable WHERE variable_id='preserved-id';", old) == before
            self.sql("INSERT INTO installation_script_variable VALUES('duplicate-id','migration-only','ACCOUNT',JSON_OBJECT('encryptedValue','second'),CURRENT_TIMESTAMP(3));", old)
            assert self.sql("SELECT COUNT(*) FROM installation_script_variable WHERE name_key='ACCOUNT';", old) == "2"
            assert self.sql("SELECT CONCAT(runtime_engine,':',execution_mode,':',entry_point) FROM plugin_definition WHERE plugin_id='upgrade-fixture';", old) == "java:plugin:example.Legacy"
        self.check("merge upgrade preserves row identity/ciphertext and permits duplicate names", merge_upgrade)
        def compare():
            columns = "SELECT CONCAT(TABLE_NAME,':',COLUMN_NAME,':',COLUMN_TYPE,':',IS_NULLABLE) FROM COLUMNS WHERE TABLE_SCHEMA='%s' ORDER BY TABLE_NAME,ORDINAL_POSITION;"
            # ALTER appends columns, so compare column definitions irrespective of physical order.
            assert sorted(self.sql(columns % name, "information_schema").splitlines()) == sorted(self.sql(columns % old, "information_schema").splitlines())
            indexes = "SELECT CONCAT(TABLE_NAME,':',INDEX_NAME,':',NON_UNIQUE,':',SEQ_IN_INDEX,':',COLUMN_NAME) FROM STATISTICS WHERE TABLE_SCHEMA='%s' ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;"
            assert self.sql(indexes % name, "information_schema") == self.sql(indexes % old, "information_schema")
        self.check("fresh and upgraded schemas have identical columns and indexes", compare)
        self.report["mysqlVersion"] = self.sql("SELECT VERSION();")
        self.save_report()

    def request(self, path, method="GET", value=None, raw=None, content_type=None, expected=200):
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if value is not None:
            raw = json.dumps(value, ensure_ascii=False).encode("utf-8")
            content_type = "application/json"
        if content_type:
            headers["Content-Type"] = content_type
        req = urllib.request.Request(self.args.base_url.rstrip("/") + path, raw, headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=90) as response:
                status, body = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, body = error.code, error.read()
        assert status == expected, f"{method} {path}: expected HTTP {expected}, got {status}: {body[:1500].decode('utf-8', 'replace')}"
        if expected != 200:
            return None
        result = json.loads(body)
        if "code" in result:
            assert result["code"] == 0, result
            return result.get("data")
        return result

    def upload(self, filename, code):
        boundary = uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()
        body += code if isinstance(code, bytes) else code.encode("utf-8")
        body += f"\r\n--{boundary}--\r\n".encode()
        return self.request("/api/plugins/artifacts", "POST", raw=body, content_type=f"multipart/form-data; boundary={boundary}")

    def install(self, plugin_id, label):
        return self.request("/api/plugins/installations", "POST", {"pluginId": plugin_id, "installationName": label})["installationId"]

    def job(self, installation, **overrides):
        body = {"jobName": "Script acceptance", "pluginInstallationId": installation,
                "cronExpression": "0 0 0 1 1 ? 2099", "misfirePolicy": "DO_NOTHING", "concurrent": False,
                "inputJson": "{}", "timeoutMillis": 30000}
        body.update(overrides)
        return self.request("/api/jobs", "POST", body)

    def start(self, job, **options):
        return self.request(f"/api/jobs/{job}/run", "POST", options)["executionId"]

    def await_execution(self, execution_id, status="SUCCESS"):
        deadline = time.monotonic() + 100
        while time.monotonic() < deadline:
            row = self.request(f"/api/job-executions/{execution_id}")
            if row["status"] not in ("RUNNING", "PENDING"):
                assert row["status"] == status, row
                if row.get("finishedAt") and row.get("durationMillis") is not None:
                    elapsed = (datetime.fromisoformat(row["finishedAt"]) - datetime.fromisoformat(row["startedAt"])).total_seconds() * 1000
                    assert abs(elapsed - row["durationMillis"]) < 5000, "Database and host execution timestamps disagree; check deployment time zones"
                return row
            time.sleep(.15)
        raise AssertionError(f"Execution {execution_id} did not finish")

    def run(self, job, status="SUCCESS", **options):
        return self.await_execution(self.start(job, **options), status)

    def logs(self, execution):
        return self.request(f'/api/job-executions/{execution["id"]}/logs?tailBytes=65536')["content"]

    def operation(self, engine, action, name=None, version=None, status="SUCCEEDED"):
        op = self.request(f"/api/plugins/script-environments/{engine}/operations", "POST", {"action": action, "name": name, "version": version})
        deadline = time.monotonic() + 600
        while time.monotonic() < deadline:
            op = self.request("/api/plugins/script-environments/operations/" + op["id"])
            if op["status"] in ("SUCCEEDED", "FAILED"):
                assert op["status"] == status, op
                return op
            time.sleep(.5)
        raise AssertionError("Dependency operation timed out")

    def authenticate(self, initialize):
        deadline = time.monotonic() + 120
        while True:
            try:
                assert self.request("/api/actuator/health")["status"] == "UP"
                break
            except (urllib.error.URLError, ConnectionError, TimeoutError, AssertionError):
                if time.monotonic() >= deadline:
                    raise
                time.sleep(1)
        if initialize and not self.args.resume:
            assert self.sql("SELECT COUNT(*) FROM sky_user;") == "0", "Refusing to initialize a nonempty deployment"
            assert not self.args.state.exists(), "Refusing to overwrite acceptance state"
            state = {"username": "script-acceptance", "password": secrets.token_urlsafe(24), "installations": [], "jobs": [], "executions": []}
            self.request("/api/user/register", "POST", {"username": state["username"], "password": state["password"], "email": "acceptance@example.test"})
            self.args.state.write_text(json.dumps(state), encoding="utf-8")
            self.args.state.chmod(0o600)
        self.state = json.loads(self.args.state.read_text(encoding="utf-8"))
        self.token = self.request("/api/user/login", "POST", {"username": self.state["username"], "password": self.state["password"]})["token"]

    def save_state(self):
        self.args.state.write_text(json.dumps(self.state), encoding="utf-8")

    def dependencies(self):
        for engine in ("node", "python"):
            self.check(engine + " initialize via API and persistent MySQL store", lambda e=engine: self.operation(e, "initialize"))
            package, first, second = ("is-number", "6.0.0", "7.0.0") if engine == "node" else ("six", "1.16.0", "1.17.0")
            self.check(engine + " install real dependency", lambda e=engine,p=package,v=first: self.operation(e, "install", p, v))
            previous = self.request(f"/api/plugins/script-environments/{engine}")["revision"]
            def failed(e=engine,p=package,r=previous):
                self.operation(e, "upgrade", p, "999999.0.0", "FAILED")
                assert self.request(f"/api/plugins/script-environments/{e}")["revision"] == r
            self.check(engine + " failed upgrade leaves current revision unchanged", failed)
            self.check(engine + " successful upgrade", lambda e=engine,p=package,v=second: self.operation(e, "upgrade", p, v))

    def sdk_plugins(self):
        for engine in ("node", "python"):
            def verify(engine=engine):
                plugin_id = "acceptance-sdk-" + engine
                entry = "main.mjs" if engine == "node" else "main.py"
                manifest = {"formatVersion": 1, "pluginId": plugin_id, "version": "1.0.0", "displayName": plugin_id,
                            "runtime": {"engine": engine, "entry": entry},
                            "capabilities": {"task": True, "config": True, "account": True, "notification": False, "service": False},
                            "configSchema": {"fields": []}, "accountSchema": {"fields": []}}
                code = '''import isNumber from 'is-number';
export async function run(ctx) {
 const config = await ctx.config.get(), accounts = await ctx.accounts.list();
 if (!isNumber('42') || accounts.length !== 1 || config.marker !== 'SDK中文') throw new Error('SDK input');
 await ctx.config.update({...config, updated:true});
 await ctx.accounts.update(accounts[0].accountId, {...accounts[0].data, updated:true});
 await ctx.data.put('acceptance','entry',{marker:config.marker});
 const value = await ctx.data.get('acceptance','entry');
 if (value.marker !== config.marker || (await ctx.data.list('acceptance')).length !== 1) throw new Error('SDK data');
 await ctx.data.delete('acceptance','entry');
 console.log('SDK中文 callbacks verified');
 return ctx.success('SDK passed',{installationId:ctx.installationId,processed:accounts.length});
}
''' if engine == "node" else '''import six
def run(ctx):
    config, accounts = ctx.config.get(), ctx.accounts.list()
    assert six.string_types == (str,) and len(accounts) == 1 and config['marker'] == 'SDK中文'
    ctx.config.update(dict(config, updated=True))
    ctx.accounts.update(accounts[0]['accountId'], dict(accounts[0]['data'], updated=True))
    ctx.data.put('acceptance','entry',{'marker':config['marker']})
    assert ctx.data.get('acceptance','entry')['marker'] == config['marker']
    assert len(ctx.data.list('acceptance')) == 1
    ctx.data.delete('acceptance','entry')
    print('SDK中文 callbacks verified',flush=True)
    return ctx.success('SDK passed',{'installationId':ctx.installation_id,'processed':len(accounts)})
'''
                content = io.BytesIO()
                with zipfile.ZipFile(content, "w") as archive:
                    archive.writestr("sky-script.json", json.dumps(manifest))
                    archive.writestr(entry, code)
                self.upload(plugin_id + ".zip", content.getvalue())
                installation = self.install(plugin_id, plugin_id)
                self.state["installations"].append(installation)
                base = "/api/plugins/installations/" + installation
                self.request(base + "/config", "PUT", {"configJson": json.dumps({"marker": "SDK中文"})})
                for account_id, enabled in (("enabled", True), ("disabled", False)):
                    self.request(base + "/accounts", "POST", {"accountId": account_id, "displayName": account_id, "enabled": enabled, "accountJson": "{}"})
                job = self.job(installation)
                row = self.run(job["id"])
                assert row["runtimeEngine"] == engine and row["executionMode"] == "plugin"
                assert row["environmentRevision"] and row["artifactSha256"] and row["interpreterVersion"]
                assert "SDK中文 callbacks verified" in self.logs(row)
                assert "true" in self.sql(f"SELECT config_json FROM plugin_config WHERE installation_id='{installation}';")
                self.state["jobs"].append(job["id"])
                self.state["executions"].append(row["id"])
                self.save_state()
            self.check(engine + " ZIP SDK/config/accounts/data callbacks through real MySQL", verify)

    def standalone(self):
        for extension in ("js", "mjs", "py"):
            def verify(extension=extension):
                code = '''import json, os, sys, time
print('SNAPSHOT:'+json.dumps({'value':os.getenv('ACCOUNTS'), 'args':sys.argv[1:], 'leaks':[k for k in os.environ if k.startswith('SKY_')]},ensure_ascii=False),flush=True)
if '--wait' in sys.argv: time.sleep(20)
if '--fail' in sys.argv: sys.exit(7)
''' if extension == "py" else '''console.log('SNAPSHOT:'+JSON.stringify({value:process.env.ACCOUNTS ?? null,args:process.argv.slice(2),leaks:Object.keys(process.env).filter(k=>k.startsWith('SKY_'))}));
if(process.argv.includes('--wait')) setTimeout(()=>{},20000);
if(process.argv.includes('--fail')) process.exitCode=7;
'''
                plugin = self.upload("acceptance." + extension, code)
                a, b = self.install(plugin["pluginId"], extension + " A"), self.install(plugin["pluginId"], extension + " B")
                self.state["installations"] += [a, b]
                base = "/api/plugins/installations/" + a
                other = "/api/plugins/installations/" + b
                def variable(path, value, **extra):
                    return self.request(path + "/script-environment-variables", "POST", {"name": "ACCOUNTS", "value": value, **extra})
                first, second = variable(base, "验收账号A"), variable(base, "验收账号B")
                foreign = variable(other, "隔离账号C")
                assert "value" not in first and "encryptedValue" not in first
                self.request(base + "/script-environment-variables/" + first["id"], "PUT", {"name": "ACCOUNTS", "remark": "retained"})
                db = self.sql(f"SELECT variable_json FROM installation_script_variable WHERE installation_id='{a}';")
                assert "验收账号A" not in db and "验收账号B" not in db and "encryptedValue" in db
                args = ["", "中文 空格", "line1\nline2", "$(echo literal)"]
                saved = self.request(base + "/script-arguments", "PUT", {"arguments": args})
                job1, job2, isolated = self.job(a), self.job(a), self.job(b)
                self.state["jobs"] += [job1["id"], job2["id"], isolated["id"]]
                def snapshot(row):
                    logs = self.logs(row)
                    line = next(line for line in logs.splitlines() if line.startswith("SNAPSHOT:"))
                    return json.loads(line[len("SNAPSHOT:"):])
                for job in (job1, job2):
                    row = self.run(job["id"])
                    assert snapshot(row) == {"value": "验收账号A@验收账号B", "args": args, "leaks": []}
                    assert row["scriptOptionsRevision"] == saved["revision"] and row["executionMode"] == "standalone"
                    self.state["executions"].append(row["id"])
                assert snapshot(self.run(isolated["id"]))["value"] == "隔离账号C"
                # Invalid cross-instance batches and oversized edits must roll back completely.
                before = self.sql(f"SELECT options_json FROM installation_script_options WHERE installation_id='{a}';")
                self.request(base + "/script-environment-variables/batch-enabled", "POST", {"ids": [first["id"], foreign["id"]], "enabled": False}, expected=404)
                self.request(base + "/script-arguments", "PUT", {"arguments": ["x" * 24576]}, expected=400)
                assert self.sql(f"SELECT options_json FROM installation_script_options WHERE installation_id='{a}';") == before
                assert all(v["enabled"] for v in self.request(base + "/script-environment-variables")["items"])
                # Fail after variable/options writes, inside the outer audit transaction.
                self.sql("CREATE TRIGGER acceptance_audit_failure BEFORE INSERT ON plugin_audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Acceptance injected audit failure';")
                try:
                    self.request(base + "/script-environment-variables", "POST", {"name": "ROLLBACK", "value": "must-not-persist"}, expected=500)
                finally:
                    self.sql("DROP TRIGGER acceptance_audit_failure;")
                assert self.sql(f"SELECT COUNT(*) FROM installation_script_variable WHERE installation_id='{a}' AND name_key='ROLLBACK';") == "0"
                assert self.sql(f"SELECT options_json FROM installation_script_options WHERE installation_id='{a}';") == before
                self.request(base + "/script-environment-settings", "PUT", {"defaultSeparator": "\n", "overrides": [{"name": "ACCOUNTS", "separator": "@@"}]})
                assert snapshot(self.run(job1["id"]))["value"] == "验收账号A@@验收账号B"
                self.request(base + "/script-environment-settings", "PUT", {"defaultSeparator": "\n", "overrides": []})
                assert snapshot(self.run(job1["id"]))["value"] == "验收账号A\n验收账号B"
                self.request(base + "/script-environment-variables/batch-enabled", "POST", {"ids": [first["id"], second["id"]], "enabled": False})
                assert snapshot(self.run(job1["id"]))["value"] is None
                self.request(base + "/script-environment-variables/batch-enabled", "POST", {"ids": [first["id"], second["id"]], "enabled": True})
                # A running process retains the old arguments and revision after configuration changes.
                old = self.request(base + "/script-arguments", "PUT", {"arguments": ["--wait", "before"]})
                # Manual run is synchronous; keep its HTTP request active while inspecting/cancelling it.
                with ThreadPoolExecutor(max_workers=1) as pool:
                    run_request = pool.submit(self.start, job1["id"])
                    deadline = time.monotonic() + 30
                    while time.monotonic() < deadline:
                        rows = self.request(f"/api/jobs/{job1['id']}/executions")["items"]
                        active = next((r for r in rows if r["status"] == "RUNNING"), None)
                        if active:
                            running = active["id"]
                            content = self.request(f"/api/job-executions/{running}/logs?tailBytes=65536")["content"]
                            if "SNAPSHOT:" in content:
                                break
                        time.sleep(.15)
                    else:
                        raise AssertionError("Waiting script did not start")
                    newer = self.request(base + "/script-arguments", "PUT", {"arguments": ["after"]})
                    self.request(f"/api/job-executions/{running}/cancel", "POST")
                    assert run_request.result(timeout=30) == running
                cancelled = self.await_execution(running, "CANCELLED")
                assert cancelled["scriptOptionsRevision"] == old["revision"]
                assert snapshot(cancelled)["args"] == ["--wait", "before"]
                row = self.run(job2["id"])
                assert row["scriptOptionsRevision"] == newer["revision"] and snapshot(row)["args"] == ["after"]
                # Concurrent HTTP writes serialize without losing revisions in MySQL.
                with ThreadPoolExecutor(max_workers=6) as pool:
                    revisions = list(pool.map(lambda n: self.request(base + "/script-arguments", "PUT", {"arguments": [f"parallel-{n}"]})["revision"], range(6)))
                assert len(set(revisions)) == 6
                assert max(map(int,revisions)) == int(newer["revision"]) + 6
                latest = self.request(base + "/script-arguments")
                row = self.run(job1["id"])
                assert row["scriptOptionsRevision"] == latest["revision"] and snapshot(row)["args"] == latest["arguments"]
                self.request(base + "/script-arguments", "PUT", {"arguments": ["--fail"]})
                failed = self.run(job1["id"], "FAILED")
                assert json.loads(failed["resultJson"])["exitCode"] == 7
                self.request(base + "/script-arguments", "PUT", {"arguments": ["--wait"]})
                self.run(job1["id"], "TIMEOUT", timeoutMillis=1200)
                self.request(base + "/script-arguments", "PUT", {"arguments": args})
                # A legacy job blocks every job in this installation until explicitly cleared.
                self.sql(f"INSERT INTO job_script_options VALUES({job1['id']},JSON_OBJECT('arguments',JSON_ARRAY('legacy')),CURRENT_TIMESTAMP(3));")
                assert str(job1["id"]) in self.request(base + "/script-arguments")["legacyJobIds"]
                legacy = self.run(job2["id"], "FAILED")
                assert "Legacy job scriptOptions" in legacy["errorMessage"]
                self.sql(f"DELETE FROM job_script_options WHERE job_id={job1['id']};")
                # Both manual and Quartz executions read the same installation configuration.
                scheduled = self.job(a, cronExpression="0/2 * * * * ?")
                deadline = time.monotonic() + 30
                while time.monotonic() < deadline:
                    rows = self.request(f"/api/jobs/{scheduled['id']}/executions")["items"]
                    good = [r for r in rows if r["status"] == "SUCCESS"]
                    if good: break
                    time.sleep(.3)
                else: raise AssertionError("Quartz did not execute scheduled script")
                self.request(f"/api/jobs/{scheduled['id']}/pause", "POST")
                assert good[0]["triggerType"] == "SCHEDULED" and snapshot(good[0])["args"] == args
                self.state["jobs"].append(scheduled["id"])
                self.state["executions"].append(good[0]["id"])
                # Remove a disposable third instance and verify cleanup of encrypted options.
                cleanup = self.install(plugin["pluginId"], extension + " cleanup")
                variable("/api/plugins/installations/" + cleanup, "cleanup")
                self.request("/api/plugins/installations/" + cleanup, "DELETE")
                assert self.sql(f"SELECT (SELECT COUNT(*) FROM installation_script_options WHERE installation_id='{cleanup}')+(SELECT COUNT(*) FROM installation_script_variable WHERE installation_id='{cleanup}');") == "0"
                self.state.setdefault("standalone", []).append({"installation": a, "job": job1["id"], "expectedArgs": args})
                self.save_state()
            self.check(extension + " standalone: encryption/merge/isolation/rollback/snapshot/concurrency/cancel/timeout/Quartz/cleanup", verify)

    def permissions(self):
        role = self.request("/api/system/roles", "POST", {"roleName": "SCRIPT_ACCEPTANCE_SELF", "description": "Disposable acceptance"})["roleId"]
        self.request(f"/api/system/roles/{role}/data-scope", "PUT", {"dataScope": "SELF", "departmentIds": []})
        self.request(f"/api/system/roles/{role}/permissions", "PUT", {"permissions": ["user:read:self", "plugin:config:read", "plugin:config:write", "plugin:runtime:read", "plugin:runtime:manage", "job:read", "job:write", "job:run", "job:execution:read"]})
        password = secrets.token_urlsafe(24)
        self.request("/api/system/users", "POST", {"username": "script-self", "password": password, "email": "self@example.test", "departmentId": 1, "enabled": True, "roles": ["SCRIPT_ACCEPTANCE_SELF"]})
        admin = self.token
        self.token = self.request("/api/user/login", "POST", {"username": "script-self", "password": password})["token"]
        try:
            base = "/api/plugins/installations/" + self.state["standalone"][0]["installation"]
            self.request(base + "/script-arguments", expected=404)
            self.request(base + "/script-environment-variables", expected=404)
            self.request(base + "/script-arguments", "PUT", {"arguments": []}, expected=404)
            self.request("/api/plugins/script-environments", expected=403)
        finally:
            self.token = admin
        self.request(f"/api/system/roles/{role}/data-scope", "PUT", {"dataScope": "ALL", "departmentIds": []})
        self.request(f"/api/system/roles/{role}/permissions", "PUT", {"permissions": ["user:read:self", "plugin:config:read"]})
        self.token = self.request("/api/user/login", "POST", {"username": "script-self", "password": password})["token"]
        try:
            self.request(base + "/script-arguments")
            self.request(base + "/script-environment-settings")
            assert all("value" not in row for row in self.request(base + "/script-environment-variables")["items"])
            self.request(base + "/script-arguments", "PUT", {"arguments": []}, expected=403)
            self.request(base, expected=403)
        finally:
            self.token = admin

    def verify(self):
        for installation in self.state["installations"]:
            self.request("/api/plugins/installations/" + installation)
        for execution in self.state["executions"]:
            assert self.request(f"/api/job-executions/{execution}")["status"] == "SUCCESS"
            assert self.request(f"/api/job-executions/{execution}/logs")["exists"]
        for item in self.state["standalone"]:
            base = "/api/plugins/installations/" + item["installation"]
            arguments = self.request(base + "/script-arguments")
            settings = self.request(base + "/script-environment-settings")
            row = self.run(item["job"])
            assert row["status"] == "SUCCESS" and row["scriptOptionsRevision"] == arguments["revision"]
            snapshot = json.loads(next(line[9:] for line in self.logs(row).splitlines() if line.startswith("SNAPSHOT:")))
            separator = next((v["separator"] for v in settings["overrides"] if v["name"] == "ACCOUNTS"), settings["defaultSeparator"])
            assert snapshot == {"value": "验收账号A" + separator + "验收账号B", "args": arguments["arguments"], "leaks": []}
        for job in self.state["jobs"][:2]:
            row = self.run(job)
            assert "SDK中文 callbacks verified" in self.logs(row)
        for engine in ("node", "python"):
            assert self.request(f"/api/plugins/script-environments/{engine}/health")["available"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["database", "initialize", "verify"])
    parser.add_argument("--database", required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:18081")
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--resume", action="store_true", help="Resume this disposable run after correcting a failed check")
    args = parser.parse_args()
    acceptance = Acceptance(args)
    if args.phase == "database":
        assert args.baseline and args.baseline.is_file(), "Provide original pre-script schema"
        acceptance.prepare_database()
    else:
        acceptance.authenticate(args.phase == "initialize")
        if args.phase == "initialize":
            acceptance.dependencies()
            acceptance.sdk_plugins()
            acceptance.standalone()
            acceptance.check("real HTTP role permissions and SELF data scope", acceptance.permissions)
        acceptance.check("persisted installations, executions, logs and environment health", acceptance.verify)
    acceptance.report["completedAt"] = datetime.now(timezone.utc).isoformat()
    acceptance.report["passed"] = all(c["passed"] for c in acceptance.report["checks"])
    acceptance.save_report()
    print("Acceptance phase passed:", args.phase, flush=True)


if __name__ == "__main__":
    main()
