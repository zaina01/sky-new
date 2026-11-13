"""Exercise a disposable deployment; never run 'initialize' against real data."""

import io
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.request
import uuid
import zipfile


def main():
    base, plugin, state_file, phase = sys.argv[1:]
    token = ""

    def request(path, method="GET", value=None, raw=None, content_type=None):
        headers = {"Accept": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if value is not None:
            raw = json.dumps(value).encode()
            content_type = "application/json"
        if content_type:
            headers["Content-Type"] = content_type
        req = urllib.request.Request(base + path, raw, headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=60) as response:
                result = json.load(response)
        except urllib.error.HTTPError as error:
            raise AssertionError(f"{method} {path}: HTTP {error.code}") from error
        if "code" in result:
            assert result["code"] == 0, result
            return result["data"]
        return result

    assert request("/api/actuator/health") == {"status": "UP"}
    # Health is public; business and non-exposed actuator endpoints are not.
    for path in ("/api/user/me", "/api/actuator/env"):
        try:
            urllib.request.urlopen(base + path, timeout=10)
        except urllib.error.HTTPError as error:
            assert error.code in (401, 403, 404), (path, error.code)
        else:
            raise AssertionError(f"Unexpected anonymous access to {path}")

    state_path = Path(state_file)
    if phase == "initialize":
        state = {"username": "docker-smoke", "password": uuid.uuid4().hex}
        request("/api/user/register", "POST", {
            **state, "email": "docker-smoke@example.test",
        })
    elif phase == "verify":
        state = json.loads(state_path.read_text())
    else:
        raise ValueError("phase must be initialize or verify")
    token = request("/api/user/login", "POST", {
        "username": state["username"], "password": state["password"],
    })["token"]
    assert request("/api/user/me")["username"] == state["username"]

    if phase == "initialize":
        # Store random padding in a valid jar to exercise uploads above 1 MiB.
        jar = io.BytesIO(Path(plugin).read_bytes())
        with zipfile.ZipFile(jar, "a", compression=zipfile.ZIP_STORED) as archive:
            archive.writestr("docker-smoke-padding.bin", os.urandom(2 * 1024 * 1024))
        boundary = uuid.uuid4().hex
        body = (
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="smoke.jar"\r\n'
            'Content-Type: application/java-archive\r\n\r\n'
        ).encode() + jar.getvalue() + f"\r\n--{boundary}--\r\n".encode()
        uploaded = request("/api/plugins/artifacts", "POST", raw=body,
                           content_type=f"multipart/form-data; boundary={boundary}")
        installation = request("/api/plugins/installations", "POST", {
            "pluginId": uploaded["pluginId"], "installationName": "Container smoke plugin",
        })
        state["installationId"] = installation["installationId"]
        request(f'/api/plugins/installations/{state["installationId"]}/config', "PUT", {
            "configJson": '{"message":"docker smoke callback verified"}',
        })
        request(f'/api/plugins/installations/{state["installationId"]}/accounts', "POST", {
            "accountId": "smoke-account", "displayName": "Smoke account", "enabled": True,
            "accountJson": '{"accountId":"smoke-account","region":"east"}',
        })
        job = request("/api/jobs", "POST", {
            "jobName": "Container smoke task",
            "pluginInstallationId": state["installationId"],
            "cronExpression": "0 0 0 1 1 ? 2099",
            "misfirePolicy": "DO_NOTHING", "concurrent": False,
            "inputJson": "{}", "timeoutMillis": 60000,
        })
        state["jobId"] = job["id"]
        execution_id = request(f'/api/jobs/{state["jobId"]}/run', "POST", {})["executionId"]
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            execution = request(f"/api/job-executions/{execution_id}")
            if execution["status"] != "RUNNING":
                break
            time.sleep(1)
        assert execution["status"] == "SUCCESS", execution
        state["executionId"] = execution_id
        state_path.write_text(json.dumps(state))

    request(f'/api/plugins/installations/{state["installationId"]}')
    request(f'/api/jobs/{state["jobId"]}')
    logs = request(f'/api/job-executions/{state["executionId"]}/logs')
    assert "docker smoke callback verified" in json.dumps(logs), logs
    assert "accounts=1" in json.dumps(logs), logs
    if phase == "initialize":
        # Leave a managed Runner alive so container shutdown exercises its cleanup.
        runtime = request(f'/api/plugins/installations/{state["installationId"]}/start', "POST")
        assert runtime["state"] == "RUNNING", runtime
    print(f"Deployment {phase} passed: auth, plugin, callbacks, task and logs.")


if __name__ == "__main__":
    main()
