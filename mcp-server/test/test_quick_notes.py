import copy
import asyncio
import json
import os
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import httpx
import pytest
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

from family_admin_mcp.client import ApiClient, ApiError
from family_admin_mcp.config import Settings
from family_admin_mcp.journal import Journal
from family_admin_mcp.service import FamilyService


class NotesBackend:
    def __init__(self):
        self.notes = {1: {"id": 1, "title": "购物", "body": "原正文", "tags": ["家用"], "imageIds": [9],
                          "tasks": [{"text": "买牛奶", "done": False}], "color": "sage", "pinned": True,
                          "status": "ACTIVE", "version": 0, "createdBy": 7}}
        self.calls, self.writes = [], []
        self.fail = None

    async def handle(self, request):
        self.calls.append(request)
        path = request.url.path
        if path == "/api/auth/login":
            return self.ok({"accessToken": "test-token"})
        assert request.headers["Authorization"] == "Bearer test-token"
        if path == "/api/auth/me":
            return self.ok({"id": 7, "familyId": 2, "displayName": "成员", "role": "FAMILY_MEMBER"})
        if request.method == "GET" and path == "/api/quick-notes":
            return self.ok(list(self.notes.values()))
        assert path.startswith("/api/quick-notes")
        self.writes.append(request)
        if self.fail == "timeout":
            raise httpx.ReadTimeout("private detail", request=request)
        if self.fail:
            return httpx.Response(self.fail)
        body = json.loads(request.content) if request.content else None
        if request.method == "POST":
            note_id = max(self.notes, default=0) + 1
            self.notes[note_id] = {"id": note_id, **body, "createdBy": 7}
            return self.ok(self.notes[note_id])
        note_id = int(path.rsplit("/", 1)[1])
        if note_id not in self.notes:
            return httpx.Response(404)
        version = body["version"] if body else int(request.url.params["version"])
        if version != self.notes[note_id]["version"]:
            return httpx.Response(409)
        if request.method == "PUT":
            self.notes[note_id] = {**self.notes[note_id], **body, "version": version + 1}
            return self.ok(self.notes[note_id])
        assert request.method == "DELETE"
        self.notes.pop(note_id)
        return self.ok(None)

    @staticmethod
    def ok(data):
        return httpx.Response(200, json={"code": "SUCCESS", "data": data})


@pytest.fixture
async def notes_setup(tmp_path):
    backend = NotesBackend()
    settings = Settings("http://localhost/api", "user", "password", tmp_path / "journal.sqlite3")
    async with httpx.AsyncClient(transport=httpx.MockTransport(backend.handle)) as http:
        yield FamilyService(ApiClient(settings, http), Journal(settings.state_path)), backend


async def test_quick_note_query_filters_and_paginates_readonly(notes_setup):
    service, backend = notes_setup
    backend.notes[2] = {**backend.notes[1], "id": 2, "status": "ARCHIVED"}
    result = await service.list_quick_notes(keyword="牛奶", status="ACTIVE", page_size=1)
    assert result["total"] == 1 and result["notes"][0]["id"] == 1 and not result["has_more"]
    assert (await service.list_quick_notes(note_id=2))["notes"][0]["status"] == "ARCHIVED"
    assert backend.writes == []


async def test_create_quick_note_payload_and_repeat_receipt(notes_setup):
    service, backend = notes_setup
    content = {"body": "记一笔想法", "tags": ["灵感"], "tasks": [{"text": "整理", "done": False}]}
    result = await service.create_quick_note("wx:new:0", content)
    assert result["status"] == "saved" and result["record"]["id"] == 2
    payload = json.loads(backend.writes[0].content)
    assert payload["version"] == 0 and payload["status"] == "ACTIVE" and payload["imageIds"] == []
    assert await service.create_quick_note("wx:new:0", content) == result
    assert len(backend.writes) == 1
    with pytest.raises(ValueError, match="编号"):
        await service.create_quick_note("wx:new:0", {"body": "不同想法"})


async def test_partial_quick_note_update_preserves_unmentioned_fields_and_deduplicates(notes_setup):
    service, backend = notes_setup
    old = copy.deepcopy(backend.notes[1])
    result = await service.update_quick_note("wx:edit:0", 1, 0, {"body": "新正文"})
    assert result["record"]["version"] == 1
    body = json.loads(backend.writes[0].content)
    assert body["body"] == "新正文" and body["version"] == 0
    for key in ("title", "tags", "imageIds", "tasks", "color", "pinned", "status"):
        assert body[key] == old[key]
    assert await service.update_quick_note("wx:edit:0", 1, 0, {"body": "新正文"}) == result
    assert len(backend.writes) == 1


async def test_quick_note_can_complete_checklist_archive_and_clear_images_explicitly(notes_setup):
    service, backend = notes_setup
    await service.update_quick_note("wx:edit:0", 1, 0, {"tasks": [{"text": "买牛奶", "done": True}], "status": "ARCHIVED", "image_ids": [], "pinned": False})
    assert backend.notes[1]["tasks"][0]["done"] is True
    assert backend.notes[1]["imageIds"] == [] and not backend.notes[1]["pinned"]
    assert backend.notes[1]["status"] == "ARCHIVED"


async def test_delete_quick_note_requires_version_and_reuses_receipt(notes_setup):
    service, backend = notes_setup
    result = await service.delete_quick_note("wx:delete:0", 1, 0)
    assert result["status"] == "deleted" and result["note_id"] == 1
    request = backend.writes[0]
    assert request.method == "DELETE" and request.url.params["version"] == "0" and not request.content
    assert await service.delete_quick_note("wx:delete:0", 1, 0) == result
    assert len(backend.writes) == 1


@pytest.mark.parametrize("operation", ["update", "delete"])
async def test_stale_quick_note_version_and_missing_note_never_write(notes_setup, operation):
    service, backend = notes_setup
    backend.notes[1]["version"] = 2
    with pytest.raises(ValueError, match="版本"):
        if operation == "update":
            await service.update_quick_note("wx:edit:0", 1, 0, {"body": "旧窗口改动"})
        else:
            await service.delete_quick_note("wx:delete:0", 1, 0)
    with pytest.raises(ValueError, match="不存在"):
        await service.delete_quick_note("wx:delete-missing:0", 999, 0)
    assert backend.writes == []


@pytest.mark.parametrize("content", [{}, {"title": " ", "tags": ["标签"]}, {"body": "x" * 10001},
    {"body": "有内容", "tags": [" "]}, {"body": "有内容", "image_ids": [True]},
    {"body": "有内容", "tasks": [{"text": " ", "done": False}]}, {"body": "内容", "pinned": "false"},
    {"body": "内容", "status": "ARCHIVED"}])
async def test_invalid_quick_note_content_never_writes(notes_setup, content):
    service, backend = notes_setup
    with pytest.raises(ValueError):
        await service.create_quick_note("wx:new:0", content)
    assert backend.writes == []


@pytest.mark.parametrize("changes", [{}, {"body": None}, {"version": 99}, {"familyId": 999}, {"tasks": [{"text": "任务", "done": "false"}]}])
async def test_invalid_quick_note_patch_never_writes(notes_setup, changes):
    service, backend = notes_setup
    with pytest.raises(ValueError):
        await service.update_quick_note("wx:edit:0", 1, 0, changes)
    assert backend.writes == []


@pytest.mark.parametrize("operation", ["create", "update", "delete"])
@pytest.mark.parametrize("failure", ["timeout", 403, 409, 500])
async def test_quick_note_write_errors_do_not_report_success_or_retry_uncertain(notes_setup, operation, failure):
    service, backend = notes_setup
    backend.fail = failure
    async def call():
        if operation == "create":
            return await service.create_quick_note("wx:op:0", {"body": "笔记"})
        if operation == "update":
            return await service.update_quick_note("wx:op:0", 1, 0, {"body": "新正文"})
        return await service.delete_quick_note("wx:op:0", 1, 0)
    with pytest.raises(ApiError):
        await call()
    if failure in ("timeout", 500):
        backend.fail = None
        with pytest.raises(ValueError, match="核实"):
            await call()
        assert len(backend.writes) == 1


@pytest.mark.parametrize("changes", [{"note_id": 0}, {"page": 0}, {"page_size": 101}, {"status": "DELETED"}, {"keyword": " "}])
async def test_invalid_quick_note_query_is_rejected(notes_setup, changes):
    service, backend = notes_setup
    with pytest.raises(ValueError):
        await service.list_quick_notes(**changes)
    assert not any(r.url.path == "/api/quick-notes" for r in backend.calls)


async def test_quick_note_cannot_be_emptied_by_patch(notes_setup):
    service, backend = notes_setup
    with pytest.raises(ValueError):
        await service.update_quick_note("wx:empty:0", 1, 0, {"title": "", "body": "", "image_ids": [], "tasks": []})
    assert backend.writes == []


async def test_quick_note_respects_fixed_family_before_writing(notes_setup):
    from dataclasses import replace
    service, backend = notes_setup
    service.client.settings = replace(service.client.settings, family_id=3)
    with pytest.raises(ValueError, match="家庭"):
        await service.create_quick_note("wx:wrong-family:0", {"body": "禁止跨家庭"})
    assert backend.writes == []


async def test_stdio_quick_note_crud_with_versions_and_preserved_images(tmp_path):
    backend = NotesBackend()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def dispatch(self):
            content = self.rfile.read(int(self.headers.get("Content-Length", "0")))
            request = httpx.Request(self.command, "http://localhost" + self.path, headers=dict(self.headers), content=content)
            response = asyncio.run(backend.handle(request))
            self.send_response(response.status_code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(response.content)))
            self.end_headers()
            self.wfile.write(response.content)

        do_GET = dispatch
        do_POST = dispatch
        do_PUT = dispatch
        do_DELETE = dispatch

    http = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    worker = threading.Thread(target=http.serve_forever, daemon=True)
    worker.start()
    project = Path(__file__).resolve().parents[1]
    env = {**os.environ, "PYTHONPATH": str(project / "src"), "FAMILY_API_URL": f"http://127.0.0.1:{http.server_port}/api",
           "FAMILY_USERNAME": "test", "FAMILY_PASSWORD": "test-password", "FAMILY_ENV_FILE": "",
           "FAMILY_STATE_PATH": str(tmp_path / "journal.sqlite3"), "FAMILY_TIMEOUT_SECONDS": "5"}
    params = StdioServerParameters(command=sys.executable, args=["-m", "family_admin_mcp.server"], env=env)
    try:
        async with stdio_client(params) as (read, write):
            async with ClientSession(read, write) as session:
                await session.initialize()
                created = await session.call_tool("create_quick_note", {"request_id": "wx:new:0", "content": {"body": "正文", "image_ids": [9], "tasks": [{"text": "买牛奶"}]}})
                assert not created.isError
                record = json.loads(created.content[0].text)["record"]
                assert record["id"] == 2 and record["version"] == 0 and record["imageIds"] == [9]
                queried = await session.call_tool("list_quick_notes", {"note_id": 2})
                assert not queried.isError and json.loads(queried.content[0].text)["total"] == 1
                args = {"request_id": "wx:edit:0", "note_id": 2, "version": 0, "changes": {"body": "修改正文"}}
                updated = await session.call_tool("update_quick_note", args)
                assert not updated.isError
                value = json.loads(updated.content[0].text)["record"]
                assert value["version"] == 1 and value["imageIds"] == [9] and len(value["tasks"]) == 1
                repeated = await session.call_tool("update_quick_note", args)
                assert not repeated.isError and len(backend.writes) == 2
                stale = await session.call_tool("update_quick_note", {**args, "request_id": "wx:stale:0"})
                assert stale.isError and len(backend.writes) == 2
                deleted = await session.call_tool("delete_quick_note", {"request_id": "wx:delete:0", "note_id": 2, "version": 1})
                assert not deleted.isError and json.loads(deleted.content[0].text)["status"] == "deleted"
                again = await session.call_tool("delete_quick_note", {"request_id": "wx:delete:0", "note_id": 2, "version": 1})
                assert not again.isError and len(backend.writes) == 3
                assert 2 not in backend.notes
    finally:
        http.shutdown()
        http.server_close()
        worker.join(timeout=5)
