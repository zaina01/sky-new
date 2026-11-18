"""Synchronous plugin SDK. Host authorization remains authoritative."""
import json
import logging
import time
from dataclasses import dataclass
from types import SimpleNamespace
import grpc
import sky_plugin_pb2 as pb


class PluginError(RuntimeError):
    pass


def encode(value):
    return json.dumps(value, ensure_ascii=False, allow_nan=False)


def decode(value):
    return json.loads(value) if value else None


@dataclass(frozen=True)
class TaskResult:
    succeeded: bool
    message: str
    data: object = None


class Context:
    def __init__(self, request, env, host, deadline, cancelled):
        self.plugin_id = env["SKY_PLUGIN_ID"]
        self.installation_id = env["SKY_PLUGIN_INSTALLATION_ID"]
        self.job_id = request.job_id
        self.request_id = request.request_id
        self.input = decode(request.input_json)
        self.cancelled = cancelled
        self._host, self._deadline = host, deadline
        self._metadata = [("authorization", "Bearer " + env["SKY_PLUGIN_TOKEN"]),
                          ("x-sky-plugin-id", self.plugin_id),
                          ("x-sky-plugin-instance-id", env["SKY_PLUGIN_INSTANCE_ID"])]
        self.config = SimpleNamespace(get=self._config_get, update=self._config_update)
        self.accounts = SimpleNamespace(list=self._accounts_list, get=self._account_get, update=self._account_update)
        self.data = SimpleNamespace(get=self._data_get, put=self._data_put, delete=self._data_delete, list=self._data_list)
        self.notifications = SimpleNamespace(list_channels=self._channels, send=self._notify)
        self.services = SimpleNamespace(call_installation=self._service)
        self.log = logging.getLogger("sky.plugin")
        self._instance = env["SKY_PLUGIN_INSTANCE_ID"]

    def _rpc(self, method, message):
        remaining = self._deadline - time.monotonic()
        if self.cancelled.is_set() or remaining <= 0:
            raise PluginError("Task cancelled or timed out")
        try:
            response = getattr(self._host, method)(message, metadata=self._metadata, timeout=remaining)
        except grpc.RpcError as error:
            raise PluginError(f"{error.code().name}: {error.details()}") from error
        if hasattr(response, "success") and not response.success:
            raise PluginError(getattr(response, "error_message", "") or response.message or "Platform request rejected")
        return response

    @staticmethod
    def success(message="", data=None): return TaskResult(True, str(message), data)
    @staticmethod
    def failure(message=""): return TaskResult(False, str(message))
    def _config_get(self): return decode(self._rpc("GetConfig", pb.ConfigRequest(plugin_id=self.plugin_id)).config_json)
    def _config_update(self, value): return decode(self._rpc("UpdateConfig", pb.ConfigRequest(plugin_id=self.plugin_id, config_json=encode(value))).config_json)
    @staticmethod
    def _account(row): return {"accountId": row.account_id, "displayName": row.display_name, "data": decode(row.account_json)}
    def _accounts_list(self): return [self._account(a) for a in self._rpc("ListAccounts", pb.AccountRequest(plugin_id=self.plugin_id)).accounts if a.enabled]
    def _account_get(self, account_id):
        rows = self._rpc("GetAccount", pb.AccountRequest(plugin_id=self.plugin_id, account_id=str(account_id))).accounts
        return self._account(rows[0]) if rows else None
    def _account_update(self, account_id, value):
        rows = self._rpc("UpdateAccount", pb.AccountRequest(plugin_id=self.plugin_id, account_id=str(account_id), account_json=encode(value))).accounts
        return self._account(rows[0]) if rows else None
    def _data_get(self, collection, key): return decode(self._rpc("GetData", pb.PluginDataRequest(plugin_id=self.plugin_id, collection=collection, key=key)).data_json)
    def _data_put(self, collection, key, value): return self._rpc("PutData", pb.PluginDataRequest(plugin_id=self.plugin_id, collection=collection, key=key, data_json=encode(value))).success
    def _data_delete(self, collection, key): return self._rpc("DeleteData", pb.PluginDataRequest(plugin_id=self.plugin_id, collection=collection, key=key)).deleted
    def _data_list(self, collection): return [{"key": r.key, "data": decode(r.data_json)} for r in self._rpc("ListData", pb.PluginDataRequest(plugin_id=self.plugin_id, collection=collection)).records]
    def _channels(self):
        return [{"channelId": r.channel_id, "channelType": r.channel_type, "displayName": r.display_name, "enabled": r.enabled}
                for r in self._rpc("ListNotificationChannels", pb.NotificationChannelListRequest(plugin_id=self.plugin_id)).channels]
    def _notify(self, options):
        response = self._rpc("SendNotification", pb.NotificationRequest(plugin_id=self.plugin_id, instance_id=self._instance,
            job_id=self.job_id, request_id=self.request_id, channel_ids=options.get("channelIds", []), title=options.get("title", ""),
            content=options.get("content", ""), severity=options.get("severity", "INFO"), labels=options.get("labels", {})))
        return {"success": response.success, "message": response.message, "receipts": [
            {"channelId": r.channel_id, "status": r.status, "message": r.message, "historyId": str(r.history_id)} for r in response.receipts]}
    def _service(self, target, operation, data):
        return decode(self._rpc("CallPluginService", pb.PluginServiceCallRequest(
            caller_plugin_id=self.plugin_id, target_installation_id=str(target), operation=operation, request_json=encode(data),
            job_id=self.job_id, request_id=self.request_id, timeout_millis=max(1, int((self._deadline-time.monotonic())*1000)))).result_json)
