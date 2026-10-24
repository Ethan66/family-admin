import os
import sys
import json
import threading
from datetime import datetime
from zoneinfo import ZoneInfo
from contextlib import asynccontextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import pytest
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


@asynccontextmanager
async def session_for(tmp_path, api_url):
    project = Path(__file__).resolve().parents[1]
    env = {**os.environ, "PYTHONPATH": str(project / "src"),
           "FAMILY_API_URL": api_url, "FAMILY_USERNAME": "test-user", "FAMILY_ENV_FILE": "",
           "FAMILY_PASSWORD": "private-test-password", "FAMILY_STATE_PATH": str(tmp_path / "state.sqlite3"),
           "FAMILY_TIMEOUT_SECONDS": "1"}
    parameters = StdioServerParameters(command=sys.executable, args=["-m", "family_admin_mcp.server"], env=env)
    async with stdio_client(parameters) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()
            yield session


async def test_stdio_discovery_and_tool_errors_are_valid_mcp(tmp_path):
    async with session_for(tmp_path, "http://127.0.0.1:1/api") as session:
            tools = {tool.name: tool for tool in (await session.list_tools()).tools}
            assert set(tools) == {"get_family_context", "add_transaction", "list_transactions",
                                 "create_daily_task", "list_daily_tasks", "complete_daily_task", "delete_daily_task", "delete_expense",
                                 "list_courses", "record_course_attendance", "list_course_records"}
            assert tools["get_family_context"].annotations.readOnlyHint is True
            assert tools["delete_daily_task"].annotations.destructiveHint is True
            assert tools["delete_expense"].annotations.destructiveHint is True
            assert tools["list_courses"].annotations.readOnlyHint is True
            assert tools["record_course_attendance"].inputSchema["properties"]["status"]["enum"] == ["ATTENDED", "LEAVE"]
            assert "request_id" in tools["add_transaction"].inputSchema["required"]
            invalid = await session.call_tool("add_transaction", {
                "request_id": "test:0", "amount": "-1", "category": "餐饮", "transaction_date": "2026-10-24",
            })
            assert invalid.isError is True
            assert "金额" in invalid.content[0].text
            offline = await session.call_tool("get_family_context", {})
            assert offline.isError is True
            assert "private-test-password" not in str(offline)


async def test_stdio_end_to_end_write_read_and_duplicate_receipt(tmp_path):
    records = []

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            if self.path == "/api/auth/login":
                self.respond({"accessToken": "test-token"})
            else:
                assert self.path == "/api/transactions"
                assert self.headers["Authorization"] == "Bearer test-token"
                record = {"id": len(records) + 1, **body}
                records.append(record)
                self.respond(record)

        def do_GET(self):
            if self.path == "/api/auth/me":
                self.respond({"id": 7, "familyId": 2, "displayName": "测试成员", "role": "FAMILY_MEMBER"})
            elif self.path == "/api/family/members":
                self.respond([{"userId": 7, "displayName": "测试成员"}])
            else:
                assert self.path.startswith("/api/transactions?")
                self.respond({"records": records, "total": len(records), "page": 1, "pageSize": 100})

        def respond(self, data):
            body = json.dumps({"code": "SUCCESS", "data": data}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    http = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    worker = threading.Thread(target=http.serve_forever, daemon=True)
    worker.start()
    try:
        async with session_for(tmp_path, f"http://127.0.0.1:{http.server_port}/api") as session:
            context = await session.call_tool("get_family_context", {})
            assert not context.isError
            assert json.loads(context.content[0].text)["timezone"] == "Asia/Shanghai"
            arguments = {"request_id": "wx:message:0", "amount": "35.10", "category": "餐饮", "transaction_date": "2026-10-24"}
            saved = await session.call_tool("add_transaction", arguments)
            assert not saved.isError
            assert json.loads(saved.content[0].text)["status"] == "saved"
            duplicate = await session.call_tool("add_transaction", arguments)
            assert not duplicate.isError
            assert len(records) == 1
            summary = await session.call_tool("list_transactions", {"from_date": "2026-10-24"})
            assert not summary.isError
            assert json.loads(summary.content[0].text)["expense"] == "35.10"
    finally:
        http.shutdown()
        http.server_close()
        worker.join(timeout=5)


async def test_stdio_course_attendance_and_leave_update_same_record(tmp_path):
    today = datetime.now(ZoneInfo("Asia/Shanghai")).date()
    saved = {}
    writes = []
    course = {"id": 31, "memberId": 7, "memberName": "测试成员", "title": "钢琴",
              "dayOfWeek": today.isoweekday(), "date": today.isoformat(), "startTime": "10:00:00",
              "endTime": "11:00:00", "status": "PENDING"}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def do_POST(self):
            assert self.path == "/api/auth/login"
            self.respond({"accessToken": "test-token"})

        def do_GET(self):
            assert self.headers["Authorization"] == "Bearer test-token"
            if self.path == "/api/auth/me":
                self.respond({"id": 7, "familyId": 2, "displayName": "测试成员", "role": "FAMILY_MEMBER"})
            elif self.path == "/api/family/members":
                self.respond([{"userId": 7, "displayName": "测试成员"}])
            elif self.path.startswith("/api/courses/records?"):
                self.respond({"records": list(saved.values()), "total": len(saved)})
            else:
                assert self.path == "/api/courses" or self.path.startswith("/api/courses?")
                self.respond([course])

        def do_PUT(self):
            assert self.path == "/api/courses/31/attendance"
            assert self.headers["Authorization"] == "Bearer test-token"
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            assert body["status"] in {"ATTENDED", "LEAVE"} and body["date"] == today.isoformat()
            writes.append(body)
            saved[body["date"]] = {"courseId": 31, "courseDate": body["date"], "status": body["status"], "note": body["note"]}
            self.respond(None)

        def respond(self, data):
            body = json.dumps({"code": "SUCCESS", "data": data}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    http = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    worker = threading.Thread(target=http.serve_forever, daemon=True)
    worker.start()
    try:
        async with session_for(tmp_path, f"http://127.0.0.1:{http.server_port}/api") as session:
            courses = await session.call_tool("list_courses", {})
            assert not courses.isError
            assert json.loads(courses.content[0].text)["courses"][0]["id"] == 31
            args = {"request_id": "wx:course:0", "course_id": 31, "course_date": today.isoformat(), "status": "ATTENDED"}
            attended = await session.call_tool("record_course_attendance", args)
            assert not attended.isError and json.loads(attended.content[0].text)["record"]["status"] == "ATTENDED"
            duplicate = await session.call_tool("record_course_attendance", args)
            assert not duplicate.isError and len(writes) == 1
            leave = await session.call_tool("record_course_attendance", {**args, "request_id": "wx:course-correction:0", "status": "LEAVE", "note": "更正为请假"})
            assert not leave.isError
            history = await session.call_tool("list_course_records", {"month": today.strftime("%Y-%m")})
            assert not history.isError
            result = json.loads(history.content[0].text)
            assert result["total"] == 1 and result["records"][0]["status"] == "LEAVE"
            assert result["has_more"] is False and len(writes) == 2
    finally:
        http.shutdown()
        http.server_close()
        worker.join(timeout=5)
