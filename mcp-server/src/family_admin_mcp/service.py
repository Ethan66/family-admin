from datetime import date, datetime, time, timedelta
from decimal import Decimal, InvalidOperation
from zoneinfo import ZoneInfo

from .client import ApiError


def iso_date(value):
    try:
        parsed = date.fromisoformat(value)
        if parsed.isoformat() != value:
            raise ValueError
        return parsed
    except (ValueError, TypeError):
        raise ValueError("日期必须为有效的 YYYY-MM-DD；先将今天、明天换算为北京时间日期") from None


def text(value, label, limit, *, required=True):
    if not isinstance(value, str) or len(value.strip()) > limit or (required and not value.strip()):
        raise ValueError(f"{label}不能为空且不能超过 {limit} 字" if required else f"{label}不能超过 {limit} 字")
    return value.strip()


def date_range(start, end, max_days):
    start, end = iso_date(start), iso_date(end or start)
    if start > end or (end - start).days >= max_days:
        raise ValueError(f"日期范围必须按先后顺序且最多 {max_days} 天")
    return start, end


class FamilyService:
    def __init__(self, client, journal, today=None):
        self.client, self.journal = client, journal
        self.today = today or (lambda: datetime.now(ZoneInfo("Asia/Shanghai")).date())

    async def context(self):
        user = await self.client.identity()
        return {"today": self.today().isoformat(), "timezone": "Asia/Shanghai", "user": user,
                "default_member_id": self.client.settings.default_member_id or (user["id"] if user["role"] == "FAMILY_MEMBER" else None),
                "course_default_member_id": self.client.settings.course_default_member_id or self.client.settings.default_member_id or (user["id"] if user["role"] == "FAMILY_MEMBER" else None),
                "members": await self.client.request("GET", "/family/members")}

    async def _member(self, user, member_id):
        if member_id is None:
            member_id = self.client.settings.default_member_id
            if member_id is None:
                if user["role"] == "SUPER_ADMIN":
                    raise ValueError("admin 未设置默认成员，请配置 FAMILY_DEFAULT_MEMBER_ID 或明确选择家庭成员")
                member_id = user["id"]
        if not isinstance(member_id, int) or isinstance(member_id, bool) or member_id <= 0:
            raise ValueError("成员编号必须是正整数")
        members = await self.client.request("GET", "/family/members")
        for member in members:
            if member["userId"] == member_id:
                return member
        raise ValueError("成员不属于当前家庭，请从家庭成员列表中选择")

    async def _write(self, user, request_id, method, path, payload, *, returns_record=False, preflight=None):
        scope = f"{self.client.settings.api_url}|{user['familyId']}|{user['id']}"
        saved = self.journal.reserve(scope, request_id, f"{method} {path}", payload)
        if saved is not None:
            return saved
        if preflight is not None:
            try:
                await preflight()
            except BaseException:
                # Only reads have happened: releasing here cannot duplicate a write.
                self.journal.release(scope, request_id)
                raise
        try:
            data = await self.client.request(method, path, json=payload)
        except ApiError as error:
            if error.definite:
                self.journal.release(scope, request_id)
            raise
        if returns_record and (not isinstance(data, dict) or not isinstance(data.get("id"), int)):
            raise ApiError("后端未返回有效记录编号；写入结果待核实")
        result = {"status": "saved", "request_id": request_id, "record": data if returns_record else payload}
        # A crash before finish leaves a pending reservation, never an automatic second POST.
        self.journal.finish(scope, request_id, result)
        return result

    async def add_transaction(self, request_id, amount, category, transaction_date, type="EXPENSE", member_id=None, note="",
                               bank_card=None, platform=None):
        try:
            value = Decimal(amount)
            if not value.is_finite() or not 0 < value < Decimal("1000000000000") or value != value.quantize(Decimal("0.01")):
                raise ValueError
        except (InvalidOperation, ValueError, TypeError):
            raise ValueError("金额必须大于 0、小于 1 万亿且最多两位小数") from None
        if type not in {"INCOME", "EXPENSE"}:
            raise ValueError("收支类型只能为 INCOME 或 EXPENSE")
        payload = {"amount": f"{value:.2f}", "type": type,
                   "category": text(category, "分类", 50), "transactionDate": iso_date(transaction_date).isoformat(),
                   "note": text(note, "备注", 500, required=False)}
        for key, option, label, allowed in (
            ("bankCard", bank_card, "银行卡", {"工商", "农业", "交通", "光大", "招商", "支付宝", "微信"}),
            ("platform", platform, "消费平台", {"支付宝", "微信", "京东", "美团", "拼多多", "淘宝", "抖音"}),
        ):
            if option is None:
                continue
            if not isinstance(option, str):
                raise ValueError(f"{label}必须从系统支持的选项中选择")
            option = option.strip()
            if not option:
                # Omit blank fields to preserve pre-upgrade idempotency fingerprints.
                continue
            if option not in allowed:
                raise ValueError(f"{label}必须从系统支持的选项中选择")
            payload[key] = option
        user = await self.client.identity()
        member = await self._member(user, member_id)
        payload["memberName"] = member["displayName"]
        return await self._write(user, request_id, "POST", "/transactions", payload, returns_record=True)

    async def list_transactions(self, from_date, to_date=None):
        start, end = date_range(from_date, to_date, 31)
        await self.client.identity()
        records, seen = [], set()
        month = start.replace(day=1)
        while month <= end:
            count = 0
            expected_total = None
            for page in range(1, 101):
                data = await self.client.request("GET", "/transactions", params={"month": month.strftime("%Y-%m"), "page": page, "pageSize": 100})
                batch, total = data["records"], data["total"]
                if expected_total is not None and total != expected_total:
                    raise ValueError("分页期间账目数量发生变化，无法保证完整统计，请重新查询")
                expected_total = total
                count += len(batch)
                for record in batch:
                    if record["id"] in seen:
                        raise ValueError("分页数据发生变化，无法保证完整统计，请重新查询")
                    seen.add(record["id"])
                    if start <= iso_date(record["transactionDate"]) <= end:
                        records.append(record)
                if count == total:
                    break
                if not batch or count > total:
                    raise ValueError("无法读取完整账目，请缩小日期范围后重试")
            else:
                raise ValueError("账目超过查询上限，无法保证完整统计")
            month = date(month.year + 1, 1, 1) if month.month == 12 else date(month.year, month.month + 1, 1)
        income = sum((Decimal(str(r["amount"])) for r in records if r["type"] == "INCOME"), Decimal(0))
        expense = sum((Decimal(str(r["amount"])) for r in records if r["type"] == "EXPENSE"), Decimal(0))
        return {"from_date": start.isoformat(), "to_date": end.isoformat(), "scope": "当前家庭全部成员",
                "income": f"{income:.2f}", "expense": f"{expense:.2f}", "balance": f"{income-expense:.2f}", "records": records}

    async def create_daily_task(self, request_id, title, task_date, task_time=None, category="", repeat_daily=False, member_id=None, note=""):
        if task_time is not None:
            try:
                if time.fromisoformat(task_time).strftime("%H:%M") != task_time:
                    raise ValueError
            except (ValueError, TypeError):
                raise ValueError("任务时间必须为有效的 HH:MM") from None
        if not isinstance(repeat_daily, bool):
            raise ValueError("是否每日重复必须为布尔值")
        payload = {"title": text(title, "任务标题", 120), "taskDate": iso_date(task_date).isoformat(),
                   "taskTime": task_time, "category": text(category, "分类", 40, required=False),
                   "repeatDaily": repeat_daily, "note": text(note, "备注", 500, required=False)}
        user = await self.client.identity()
        payload["memberId"] = (await self._member(user, member_id))["userId"]
        return await self._write(user, request_id, "POST", "/daily-tasks", payload)

    async def list_daily_tasks(self, from_date, to_date=None, member_id=None, completed=None):
        start, end = date_range(from_date, to_date, 42)
        user = await self.client.identity()
        params = {"from": start.isoformat(), "to": end.isoformat()}
        if member_id is not None:
            params["memberId"] = (await self._member(user, member_id))["userId"]
        if completed is not None and not isinstance(completed, bool):
            raise ValueError("完成状态必须为布尔值")
        tasks = await self.client.request("GET", "/daily-tasks", params=params)
        if completed is not None:
            tasks = [task for task in tasks if task["completed"] == completed]
        return {"from_date": start.isoformat(), "to_date": end.isoformat(), "tasks": tasks}

    async def complete_daily_task(self, request_id, task_id, task_date, completed=True):
        if not isinstance(task_id, int) or isinstance(task_id, bool) or task_id <= 0 or not isinstance(completed, bool):
            raise ValueError("任务编号必须是正整数，完成状态必须为布尔值")
        payload = {"date": iso_date(task_date).isoformat(), "completed": completed}
        user = await self.client.identity()
        return await self._write(user, request_id, "PUT", f"/daily-tasks/{task_id}/completion", payload)

    async def delete_daily_task(self, request_id, task_id):
        if not isinstance(task_id, int) or isinstance(task_id, bool) or task_id <= 0:
            raise ValueError("任务编号必须是正整数")
        user = await self.client.identity()
        result = await self._write(user, request_id, "DELETE", f"/daily-tasks/{task_id}", None)
        return {**result, "status": "deleted", "task_id": task_id}

    async def delete_expense(self, request_id, transaction_id, transaction_date):
        if not isinstance(transaction_id, int) or isinstance(transaction_id, bool) or transaction_id <= 0:
            raise ValueError("支出记录编号必须是正整数")
        transaction_date = iso_date(transaction_date).isoformat()
        user = await self.client.identity()

        async def validate_expense():
            records = (await self.list_transactions(transaction_date))["records"]
            record = next((record for record in records if record["id"] == transaction_id), None)
            if record is None:
                raise ValueError("指定日期没有找到该支出记录，请先查询核对")
            if record["type"] != "EXPENSE":
                raise ValueError("该记录不是支出，不能使用删除支出工具")

        result = await self._write(user, request_id, "DELETE", f"/transactions/{transaction_id}", None,
                                   preflight=validate_expense)
        return {**result, "status": "deleted", "transaction_id": transaction_id}

    async def list_courses(self, member_id=None):
        user = await self.client.identity()
        member = await self._member(user, self.client.settings.course_default_member_id if member_id is None else member_id)
        courses = await self.client.request("GET", "/courses", params={"memberId": member["userId"]})
        today = self.today()
        monday = today - timedelta(days=today.weekday())
        return {"member_id": member["userId"], "week_start": monday.isoformat(),
                "week_end": (monday + timedelta(days=6)).isoformat(), "courses": courses}

    async def record_course_attendance(self, request_id, course_id, course_date, status, note=""):
        if not isinstance(course_id, int) or isinstance(course_id, bool) or course_id <= 0:
            raise ValueError("课程编号必须是正整数，请先查询课程表")
        selected_date = iso_date(course_date)
        if selected_date > self.today():
            raise ValueError("只能记录今天或过去的上课/请假状态，不能记录未来日期")
        if status not in {"ATTENDED", "LEAVE"}:
            raise ValueError("课程记录状态只能为 ATTENDED（已上课）或 LEAVE（已请假）")
        payload = {"date": selected_date.isoformat(), "status": status, "note": text(note, "备注", 500, required=False)}
        user = await self.client.identity()

        async def validate_course():
            courses = await self.client.request("GET", "/courses")
            course = next((course for course in courses if course["id"] == course_id), None)
            if course is None:
                raise ValueError("当前家庭没有找到该课程，请先查询课程表并核对成员和课程编号")
            if course["dayOfWeek"] != selected_date.isoweekday():
                raise ValueError("记录日期的星期与课程安排不一致，请核对后再提交")

        result = await self._write(user, request_id, "PUT", f"/courses/{course_id}/attendance", payload,
                                   preflight=validate_course)
        return {**result, "course_id": course_id}

    async def list_course_records(self, member_id=None, page=1, title=None, status=None, month=None):
        if not isinstance(page, int) or isinstance(page, bool) or page < 1:
            raise ValueError("页码必须是正整数")
        if status is not None and status not in {"ATTENDED", "LEAVE"}:
            raise ValueError("课程记录状态只能为 ATTENDED 或 LEAVE")
        if month is not None:
            if not isinstance(month, str) or len(month) != 7:
                raise ValueError("月份必须为 YYYY-MM")
            iso_date(month + "-01")
        params = {"page": page}
        if title is not None:
            params["title"] = text(title, "课程名称", 120)
        if status is not None:
            params["status"] = status
        if month is not None:
            params["month"] = month
        user = await self.client.identity()
        member = await self._member(user, self.client.settings.course_default_member_id if member_id is None else member_id)
        params["memberId"] = member["userId"]
        data = await self.client.request("GET", "/courses/records", params=params)
        return {"member_id": member["userId"], "page": page, "page_size": 20, "total": data["total"],
                "has_more": page * 20 < data["total"], "records": data["records"]}

    async def list_passbooks(self, member_id=None, start_date=None, end_date=None, page=1, page_size=20,
                             sort="DEPOSIT_DATE_DESC"):
        if not isinstance(page, int) or isinstance(page, bool) or page < 1:
            raise ValueError("页码必须是正整数")
        if not isinstance(page_size, int) or isinstance(page_size, bool) or not 1 <= page_size <= 100:
            raise ValueError("每页条数必须是1至100的整数")
        if sort not in {"DEPOSIT_DATE_DESC", "DEPOSIT_DATE_ASC", "MATURITY_DATE_ASC", "MATURITY_DATE_DESC"}:
            raise ValueError("存折排序只能选择存款日期或到期日期的升序、降序")
        start = iso_date(start_date) if start_date is not None else None
        end = iso_date(end_date) if end_date is not None else None
        if start is not None and end is not None and start > end:
            raise ValueError("存款开始日期不能晚于结束日期")
        params = {"page": page, "pageSize": page_size, "sort": sort}
        if start is not None:
            params["startDate"] = start.isoformat()
        if end is not None:
            params["endDate"] = end.isoformat()
        user = await self.client.identity()
        scope = "当前家庭全部成员"
        if member_id is not None:
            member = await self._member(user, member_id)
            params["memberId"] = member["userId"]
            scope = "当前家庭成员：" + member["displayName"]
        # This path is deliberately GET-only; no generic method/path arguments or write journal.
        data = await self.client.request("GET", "/passbooks", params=params, exact_decimals=True)

        def number(value, *, money=True):
            try:
                value = Decimal(str(value))
                if not value.is_finite():
                    raise ValueError
                return format(value, ".2f" if money else "f")
            except (InvalidOperation, ValueError, TypeError):
                raise ApiError("存折查询金额回执格式不正确，请重新查询") from None

        records = []
        for row in data["records"]:
            record = {**row}
            for key in ("amount", "totalInterest", "totalAmount"):
                record[key] = number(record[key])
            record["interestRate"] = number(record["interestRate"], money=False)
            records.append(record)
        return {"scope": scope, "member_id": member_id, "start_date": start_date, "end_date": end_date,
                "sort": sort, "page": data["page"], "page_size": data["pageSize"], "total": data["total"],
                "has_more": data["page"] * data["pageSize"] < data["total"], "records": records,
                "total_principal": number(data["totalPrincipal"]), "total_amount": number(data["totalAmount"]),
                "totals_scope": "当前家庭及筛选条件内全部存折，不限当前页", "date_filter": "存款日期（含首尾）"}
