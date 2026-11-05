"""当前教学课时的只读查询，不处理资金或课时调整。"""

from mcp.server.fastmcp import Context


COURSE_QUERY_INSTRUCTIONS = (
    "查询当前剩余课时、课程余额或还有几节课时必须调用list_course_balances，完整列出返回的各科目；不从课表或历史记录推算。"
)


class CourseQueriesMixin:
    async def list_course_balances(self, member_id=None):
        user = await self.client.identity()
        member = await self._member(user, self.client.settings.course_default_member_id if member_id is None else member_id)
        # 查询当前课时余额，包含没有固定课表的科目；不能用出勤历史快照替代。
        balances = await self.client.request("GET", "/courses/balances", params={"memberId": member["userId"]})
        return {"member_id": member["userId"], "balances": balances}


def register_course_queries(server, invoke, read):
    @server.tool(annotations=read)
    async def list_course_balances(ctx: Context, member_id: int | None = None) -> dict:
        """查询指定成员全部科目的当前剩余课时和总课时，默认课程专用成员（course_default_member_id）。

        包含没有固定课表的独立余额科目；完整列出各科目，不能只回答最近上课的科目。
        remainingLessons或totalLessons为null表示未设置，不能当作0或自行推算；balances为空表示该成员没有课程余额条目。
        历史某次上课后的余额快照另查list_course_records。本工具只读，不调整课时。
        """
        return await invoke(ctx, "list_course_balances", member_id=member_id)
