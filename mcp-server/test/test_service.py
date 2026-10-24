import asyncio
import json
from datetime import date

import httpx
import pytest

from family_admin_mcp.client import ApiClient, ApiError
from family_admin_mcp.config import Settings
from family_admin_mcp.journal import Journal
from family_admin_mcp.service import FamilyService


class Backend:
    def __init__(self):
        self.calls = []
        self.writes = []
        self.fail = None
        self.role = "FAMILY_MEMBER"
        self.expire_once = False
        self.expired_status = 401
        self.family_id = 2
        self.pages = {1: {"records": [], "total": 0}}
        self.courses = [{"id": 31, "memberId": 7, "memberName": "小明", "title": "钢琴",
                         "dayOfWeek": 6, "date": "2026-10-24", "startTime": "10:00:00", "endTime": "11:00:00", "status": "PENDING"}]
        self.course_records = {"records": [], "total": 0}

    async def handle(self, request):
        self.calls.append(request)
        path = request.url.path
        if path == "/api/auth/login":
            return self.ok({"accessToken": "secret-token"})
        if self.expire_once:
            self.expire_once = False
            return httpx.Response(self.expired_status)
        assert request.headers["Authorization"] == "Bearer secret-token"
        if path == "/api/auth/me":
            return self.ok({"id": 7, "familyId": self.family_id, "displayName": "小明", "role": self.role})
        if path == "/api/family/members":
            return self.ok([{"userId": 7, "displayName": "小明"}, {"userId": 8, "displayName": "小红"}])
        if request.method == "GET" and path == "/api/transactions":
            return self.ok(self.pages[int(request.url.params["page"])])
        if request.method == "GET" and path == "/api/daily-tasks":
            return self.ok([{"id": 11, "memberId": 7, "taskDate": "2026-10-24", "completed": False}])
        if request.method == "GET" and path == "/api/courses":
            member = request.url.params.get("memberId")
            return self.ok([c for c in self.courses if member is None or str(c["memberId"]) == member])
        if request.method == "GET" and path == "/api/courses/records":
            return self.ok(self.course_records)
        self.writes.append(request)
        if self.fail == "timeout":
            raise httpx.ReadTimeout("private transport details", request=request)
        if self.fail == "server":
            return httpx.Response(500, text="private server details")
        if self.fail == "forbidden":
            return httpx.Response(403, json={"code": "FORBIDDEN", "message": "private backend details"})
        if self.fail == "malformed":
            return httpx.Response(200, text="not json")
        if self.fail == "missing_record":
            return self.ok(None)
        if path == "/api/transactions":
            return self.ok({"id": 99, **json.loads(request.content)})
        return self.ok(None)

    @staticmethod
    def ok(data):
        return httpx.Response(200, json={"code": "SUCCESS", "data": data})


@pytest.fixture
async def setup(tmp_path):
    backend = Backend()
    settings = Settings("http://localhost:8080/api", "user", "password", tmp_path / "state.sqlite3")
    async with httpx.AsyncClient(transport=httpx.MockTransport(backend.handle)) as http:
        client = ApiClient(settings, http)
        service = FamilyService(client, Journal(settings.state_path), today=lambda: date(2026, 10, 24))
        yield service, backend, settings


def add(service, key="wx-message:0", **changes):
    return service.add_transaction(**{
        "request_id": key, "amount": "35.10", "category": "餐饮", "transaction_date": "2026-10-24", **changes,
    })


async def test_context_returns_beijing_date_and_bound_member(setup):
    service, _, _ = setup
    result = await service.context()
    assert result["today"] == "2026-10-24"
    assert result["timezone"] == "Asia/Shanghai"
    assert result["user"]["id"] == 7
    assert len(result["members"]) == 2


async def test_write_uses_backend_identity_and_exact_amount(setup):
    service, backend, _ = setup
    result = await add(service)
    body = json.loads(backend.writes[0].content)
    assert body == {"amount": "35.10", "type": "EXPENSE", "category": "餐饮", "transactionDate": "2026-10-24", "memberName": "小明", "note": ""}
    assert result["status"] == "saved"
    assert result["record"]["id"] == 99
    assert "secret-token" not in json.dumps(result)


@pytest.mark.parametrize("changes", [
    {"amount": "0"}, {"amount": "-1"}, {"amount": "1.001"}, {"amount": "NaN"},
    {"amount": "Infinity"}, {"amount": "1000000000000"}, {"transaction_date": "今天"}, {"transaction_date": "2026-02-30"},
    {"category": " "}, {"member_id": 999}, {"type": "TRANSFER"}, {"request_id": ""},
])
async def test_invalid_input_never_writes(setup, changes):
    service, backend, _ = setup
    with pytest.raises(ValueError):
        await add(service, **changes)
    assert backend.writes == []


async def test_success_is_reused_after_restart_and_conflicting_key_rejected(setup):
    service, backend, settings = setup
    first = await add(service)
    restarted = FamilyService(service.client, Journal(settings.state_path))
    assert await add(restarted) == first
    with pytest.raises(ValueError, match="编号"):
        await add(restarted, amount="36")
    assert len(backend.writes) == 1


async def test_concurrent_duplicate_is_sent_only_once(setup):
    service, backend, _ = setup
    results = await asyncio.gather(add(service), add(service), return_exceptions=True)
    assert any(isinstance(r, dict) and r["status"] == "saved" for r in results)
    assert len(backend.writes) == 1


@pytest.mark.parametrize("failure", ["timeout", "server", "malformed", "missing_record"])
async def test_uncertain_write_blocks_retry_instead_of_duplicating(setup, failure):
    service, backend, _ = setup
    backend.fail = failure
    with pytest.raises(ApiError):
        await add(service)
    backend.fail = None
    with pytest.raises(ValueError, match="核实"):
        await add(service)
    assert len(backend.writes) == 1


async def test_definitive_rejection_allows_same_request_to_be_retried(setup):
    service, backend, _ = setup
    backend.fail = "forbidden"
    with pytest.raises(ApiError) as error:
        await add(service)
    assert "private" not in str(error.value)
    backend.fail = None
    assert (await add(service))["status"] == "saved"


@pytest.mark.parametrize("status", [401, 403])
async def test_expired_token_reauthenticates_once(setup, status):
    service, backend, _ = setup
    backend.expire_once = True
    backend.expired_status = status
    await service.context()
    assert sum(r.url.path == "/api/auth/login" for r in backend.calls) == 2


async def test_super_admin_is_rejected_before_business_access(setup):
    service, backend, _ = setup
    backend.role = "SUPER_ADMIN"
    with pytest.raises(ValueError, match="FAMILY_ID"):
        await add(service)
    assert backend.writes == []


async def test_admin_is_bound_to_configured_family_and_default_member(setup):
    service, backend, settings = setup
    from dataclasses import replace
    backend.role = "SUPER_ADMIN"
    service.client.settings = replace(settings, family_id=2, default_member_id=8)
    result = await add(service)
    assert result["record"]["memberName"] == "小红"
    assert all(r.headers.get("X-Family-Id") == "2" for r in backend.calls if r.url.path != "/api/auth/login")


async def test_configured_family_mismatch_is_rejected(setup):
    service, backend, settings = setup
    from dataclasses import replace
    service.client.settings = replace(settings, family_id=3)
    with pytest.raises(ValueError, match="家庭"):
        await add(service)
    assert backend.writes == []


async def test_daily_totals_include_all_pages_and_exclude_other_dates(setup):
    service, backend, _ = setup
    backend.pages = {
        1: {"records": [{"id": 1, "transactionDate": "2026-10-24", "type": "EXPENSE", "amount": "0.10"}], "total": 3},
        2: {"records": [{"id": 2, "transactionDate": "2026-10-24", "type": "EXPENSE", "amount": "0.20"},
                        {"id": 3, "transactionDate": "2026-10-23", "type": "INCOME", "amount": "10"}], "total": 3},
    }
    result = await service.list_transactions("2026-10-24")
    assert result["expense"] == "0.30"
    assert result["income"] == "0.00"
    assert len(result["records"]) == 2


async def test_incomplete_pagination_does_not_return_false_total(setup):
    service, backend, _ = setup
    backend.pages = {1: {"records": [], "total": 1}}
    with pytest.raises(ValueError, match="完整"):
        await service.list_transactions("2026-10-24")


async def test_changed_total_across_pages_does_not_return_mixed_summary(setup):
    service, backend, _ = setup
    backend.pages = {
        1: {"records": [{"id": 1, "transactionDate": "2026-10-24", "type": "EXPENSE", "amount": "1"}], "total": 2},
        2: {"records": [{"id": 2, "transactionDate": "2026-10-24", "type": "EXPENSE", "amount": "2"},
                        {"id": 3, "transactionDate": "2026-10-24", "type": "EXPENSE", "amount": "3"}], "total": 3},
    }
    with pytest.raises(ValueError, match="完整"):
        await service.list_transactions("2026-10-24")


async def test_task_create_uses_real_member_and_does_not_invent_id(setup):
    service, backend, _ = setup
    result = await service.create_daily_task("wx:task:0", "交电费", "2026-10-25", task_time="20:00")
    body = json.loads(backend.writes[0].content)
    assert body["memberId"] == 7
    assert body["repeatDaily"] is False
    assert body["taskTime"] == "20:00"
    assert result["status"] == "saved"
    assert "id" not in result["record"]


async def test_task_query_and_completion_retain_occurrence_date(setup):
    service, backend, _ = setup
    assert len((await service.list_daily_tasks("2026-10-24", completed=False))["tasks"]) == 1
    result = await service.complete_daily_task("wx:done:0", 11, "2026-10-24")
    assert result["status"] == "saved"
    assert backend.writes[0].url.path == "/api/daily-tasks/11/completion"
    assert json.loads(backend.writes[0].content) == {"date": "2026-10-24", "completed": True}


async def test_delete_task_uses_existing_endpoint_and_deduplicates(setup):
    service, backend, _ = setup
    first = await service.delete_daily_task("wx:delete:0", 11)
    assert first["status"] == "deleted"
    assert first["task_id"] == 11
    assert await service.delete_daily_task("wx:delete:0", 11) == first
    assert len(backend.writes) == 1
    assert backend.writes[0].method == "DELETE"
    assert backend.writes[0].url.path == "/api/daily-tasks/11"
    assert backend.writes[0].content == b""
    with pytest.raises(ValueError, match="编号"):
        await service.delete_daily_task("wx:delete:0", 12)


@pytest.mark.parametrize("task_id", [0, -1, True, "11"])
async def test_invalid_delete_task_id_never_writes(setup, task_id):
    service, backend, _ = setup
    with pytest.raises(ValueError):
        await service.delete_daily_task("wx:delete:0", task_id)
    assert backend.writes == []


async def test_delete_timeout_is_not_reported_as_deleted_and_blocks_retry(setup):
    service, backend, _ = setup
    backend.fail = "timeout"
    with pytest.raises(ApiError):
        await service.delete_daily_task("wx:delete:0", 11)
    backend.fail = None
    with pytest.raises(ValueError, match="核实"):
        await service.delete_daily_task("wx:delete:0", 11)
    assert len(backend.writes) == 1


async def test_delete_backend_permission_is_not_bypassed(setup):
    service, backend, _ = setup
    backend.fail = "forbidden"
    with pytest.raises(ApiError, match="权限"):
        await service.delete_daily_task("wx:delete:0", 11)


async def test_delete_expense_validates_record_and_reuses_success_receipt(setup):
    service, backend, _ = setup
    backend.pages = {1: {"records": [{"id": 22, "transactionDate": "2026-10-24", "type": "EXPENSE", "amount": "35.10"}], "total": 1}}
    result = await service.delete_expense("wx:expense-delete:0", 22, "2026-10-24")
    assert result["status"] == "deleted" and result["transaction_id"] == 22
    assert backend.writes[0].method == "DELETE"
    assert backend.writes[0].url.path == "/api/transactions/22"
    assert backend.writes[0].content == b""
    backend.pages = {1: {"records": [], "total": 0}}
    assert await service.delete_expense("wx:expense-delete:0", 22, "2026-10-24") == result
    assert len(backend.writes) == 1


@pytest.mark.parametrize("kind,record_date", [("INCOME", "2026-10-24"), ("EXPENSE", "2026-10-23")])
async def test_delete_expense_rejects_income_and_date_mismatch(setup, kind, record_date):
    service, backend, _ = setup
    backend.pages = {1: {"records": [{"id": 22, "transactionDate": record_date, "type": kind, "amount": "35"}], "total": 1}}
    with pytest.raises(ValueError):
        await service.delete_expense("wx:expense-delete:0", 22, "2026-10-24")
    assert backend.writes == []


async def test_failed_expense_lookup_does_not_reserve_request_forever(setup):
    service, backend, _ = setup
    with pytest.raises(ValueError, match="没有找到"):
        await service.delete_expense("wx:expense-delete:0", 22, "2026-10-24")
    backend.pages = {1: {"records": [{"id": 22, "transactionDate": "2026-10-24", "type": "EXPENSE", "amount": "35"}], "total": 1}}
    result = await service.delete_expense("wx:expense-delete:0", 22, "2026-10-24")
    assert result["status"] == "deleted"
    assert len(backend.writes) == 1


@pytest.mark.parametrize("transaction_id", [0, -1, True, "22"])
async def test_invalid_expense_delete_id_never_writes(setup, transaction_id):
    service, backend, _ = setup
    with pytest.raises(ValueError):
        await service.delete_expense("wx:expense-delete:0", transaction_id, "2026-10-24")
    assert backend.writes == []


@pytest.mark.parametrize("failure", ["timeout", "forbidden"])
async def test_expense_delete_error_never_reports_success(setup, failure):
    service, backend, _ = setup
    backend.pages = {1: {"records": [{"id": 22, "transactionDate": "2026-10-24", "type": "EXPENSE", "amount": "35"}], "total": 1}}
    backend.fail = failure
    with pytest.raises(ApiError):
        await service.delete_expense("wx:expense-delete:0", 22, "2026-10-24")
    if failure == "timeout":
        backend.fail = None
        with pytest.raises(ValueError, match="核实"):
            await service.delete_expense("wx:expense-delete:0", 22, "2026-10-24")
        assert len(backend.writes) == 1


async def test_course_schedule_uses_default_member_and_exposes_week_scope(setup):
    service, backend, settings = setup
    from dataclasses import replace
    service.client.settings = replace(settings, default_member_id=8)
    result = await service.list_courses()
    request = next(r for r in backend.calls if r.url.path == "/api/courses")
    assert request.url.params["memberId"] == "8"
    assert result["member_id"] == 8
    assert result["week_start"] == "2026-10-19" and result["week_end"] == "2026-10-25"
    assert result["courses"] == []


async def test_course_default_is_separate_from_expense_and_task_defaults(setup):
    service, backend, settings = setup
    from dataclasses import replace
    service.client.settings = replace(settings, default_member_id=7, course_default_member_id=8)
    context = await service.context()
    assert context["default_member_id"] == 7 and context["course_default_member_id"] == 8
    assert (await service.list_courses())["member_id"] == 8
    assert (await service.list_course_records())["member_id"] == 8
    await add(service)
    await service.create_daily_task("wx:task:0", "交电费", "2026-10-24")
    assert json.loads(backend.writes[0].content)["memberName"] == "小明"
    assert json.loads(backend.writes[1].content)["memberId"] == 7


async def test_explicit_course_member_overrides_course_default(setup):
    service, _, settings = setup
    from dataclasses import replace
    service.client.settings = replace(settings, course_default_member_id=8)
    assert (await service.list_courses(member_id=7))["member_id"] == 7
    assert (await service.list_course_records(member_id=7))["member_id"] == 7


@pytest.mark.parametrize("status", ["ATTENDED", "LEAVE"])
async def test_course_attendance_sends_expected_payload_and_deduplicates(setup, status):
    service, backend, _ = setup
    result = await service.record_course_attendance("wx:course:0", 31, "2026-10-24", status, note="补记")
    assert result["status"] == "saved" and result["course_id"] == 31
    request = backend.writes[0]
    assert request.method == "PUT" and request.url.path == "/api/courses/31/attendance"
    assert json.loads(request.content) == {"date": "2026-10-24", "status": status, "note": "补记"}
    backend.courses = []
    assert await service.record_course_attendance("wx:course:0", 31, "2026-10-24", status, note="补记") == result
    assert len(backend.writes) == 1
    with pytest.raises(ValueError, match="编号"):
        await service.record_course_attendance("wx:course:0", 31, "2026-10-24", "LEAVE" if status == "ATTENDED" else "ATTENDED")


async def test_course_attendance_accepts_past_matching_weekday(setup):
    service, backend, _ = setup
    await service.record_course_attendance("wx:course:0", 31, "2026-10-17", "LEAVE")
    assert json.loads(backend.writes[0].content)["date"] == "2026-10-17"


@pytest.mark.parametrize("changes", [
    {"course_id": 0}, {"course_id": True}, {"course_id": "31"}, {"course_id": 999},
    {"course_date": "2026-10-31"}, {"course_date": "2026-10-23"}, {"course_date": "今天"},
    {"status": "PENDING"}, {"status": "已上课"}, {"note": "x" * 501},
])
async def test_invalid_course_attendance_never_writes(setup, changes):
    service, backend, _ = setup
    args = {"request_id": "wx:course:0", "course_id": 31, "course_date": "2026-10-24", "status": "ATTENDED", **changes}
    with pytest.raises(ValueError):
        await service.record_course_attendance(**args)
    assert backend.writes == []


@pytest.mark.parametrize("failure", ["timeout", "forbidden", "server"])
async def test_failed_course_attendance_is_not_reported_successful(setup, failure):
    service, backend, _ = setup
    backend.fail = failure
    with pytest.raises(ApiError):
        await service.record_course_attendance("wx:course:0", 31, "2026-10-24", "ATTENDED")
    if failure != "forbidden":
        backend.fail = None
        with pytest.raises(ValueError, match="核实"):
            await service.record_course_attendance("wx:course:0", 31, "2026-10-24", "ATTENDED")
        assert len(backend.writes) == 1


async def test_course_history_passes_filters_and_exposes_pagination(setup):
    service, backend, _ = setup
    backend.course_records = {"records": [{"id": 99, "status": "LEAVE", "courseDate": "2026-10-17"}], "total": 21}
    result = await service.list_course_records(page=2, title="钢琴", status="LEAVE", month="2026-10")
    request = next(r for r in backend.calls if r.url.path == "/api/courses/records")
    assert dict(request.url.params) == {"memberId": "7", "page": "2", "title": "钢琴", "status": "LEAVE", "month": "2026-10"}
    assert result["page_size"] == 20 and result["total"] == 21
    assert result["has_more"] is False and result["records"][0]["status"] == "LEAVE"


@pytest.mark.parametrize("changes", [{"page": 0}, {"page": True}, {"status": "PENDING"}, {"month": "2026-13"}, {"month": "0000-01"}, {"title": " "}, {"member_id": 999}])
async def test_invalid_course_history_filters_rejected(setup, changes):
    service, backend, _ = setup
    with pytest.raises(ValueError):
        await service.list_course_records(**changes)
    assert backend.writes == []


@pytest.mark.parametrize("operation", [
    lambda s: s.list_daily_tasks("2026-10-24", "2026-12-06"),
    lambda s: s.list_transactions("2026-10-24", "2026-10-23"),
    lambda s: s.create_daily_task("wx:bad", "任务", "2026-10-24", task_time="25:00"),
])
async def test_invalid_range_or_time_rejected(setup, operation):
    service, backend, _ = setup
    with pytest.raises(ValueError):
        await operation(service)
    assert backend.writes == []
