import asyncio

import httpx

from .config import Settings


class ApiError(Exception):
    def __init__(self, message: str, *, definite: bool = False):
        super().__init__(message)
        self.definite = definite


class ApiClient:
    def __init__(self, settings: Settings, http: httpx.AsyncClient):
        self.settings = settings
        self.http = http
        self._token = None
        self._login_lock = asyncio.Lock()

    async def _send(self, method, path, *, token=None, params=None, json=None):
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        if token and self.settings.family_id is not None:
            headers["X-Family-Id"] = str(self.settings.family_id)
        try:
            response = await self.http.request(
                method, self.settings.api_url + path,
                headers=headers,
                params=params, json=json, timeout=self.settings.timeout, follow_redirects=False,
            )
        except httpx.HTTPError:
            raise ApiError("后端连接中断或超时；写入结果可能不确定，请查询记录核实，勿换编号重试") from None
        return response

    @staticmethod
    def _decode(response):
        if response.status_code >= 500 or 300 <= response.status_code < 400:
            raise ApiError("后端异常或发生重定向；写入结果待核实，勿换编号重试")
        if response.status_code >= 400:
            messages = {401: "账号登录失效", 403: "没有操作权限", 404: "接口或记录不存在"}
            # Only explicit client rejection proves that the write was not accepted.
            raise ApiError(messages.get(response.status_code, "后端拒绝请求，请检查输入"),
                           definite=response.status_code in {400, 401, 403, 404, 405, 409, 422, 429})
        try:
            body = response.json()
        except ValueError:
            raise ApiError("后端回执无法解析；写入结果待核实") from None
        if not isinstance(body, dict):
            raise ApiError("后端回执格式不正确；写入结果待核实")
        if body.get("code") != "SUCCESS":
            code = body.get("code")
            messages = {"FORBIDDEN": "没有操作权限", "NOT_FOUND": "记录不存在",
                        "VALIDATION_ERROR": "参数校验失败", "UNAUTHORIZED": "账号登录失效"}
            raise ApiError(messages.get(code, "后端未确认操作成功；写入结果待核实"), definite=code in messages)
        return body.get("data")

    async def _login(self, expired_token=None):
        async with self._login_lock:
            if self._token and self._token != expired_token:
                return
            response = await self._send("POST", "/auth/login", json={
                "username": self.settings.username, "password": self.settings.password,
            })
            data = self._decode(response)
            if not isinstance(data, dict) or not isinstance(data.get("accessToken"), str) or not data["accessToken"]:
                raise ApiError("登录回执缺少有效凭证")
            self._token = data["accessToken"]

    async def request(self, method, path, *, params=None, json=None):
        if not self._token:
            await self._login()
        token = self._token
        response = await self._send(method, path, token=token, params=params, json=json)
        # This backend's Spring Security also returns 403 for expired JWTs.
        if response.status_code in {401, 403}:
            await self._login(expired_token=token)
            response = await self._send(method, path, token=self._token, params=params, json=json)
        return self._decode(response)

    async def identity(self):
        user = await self.request("GET", "/auth/me")
        if not isinstance(user, dict) or user.get("role") not in {"FAMILY_MEMBER", "SUPER_ADMIN"}:
            raise ValueError("MCP 账号身份不受支持")
        if user["role"] == "SUPER_ADMIN" and self.settings.family_id is None:
            raise ValueError("超级管理员必须在服务端配置 FAMILY_ID，固定操作一个家庭")
        if not user.get("familyId") or (self.settings.family_id is not None and user["familyId"] != self.settings.family_id):
            raise ValueError("账号返回的家庭与服务端绑定家庭不一致")
        if not isinstance(user.get("id"), int) or not user.get("displayName"):
            raise ApiError("后端账号信息不完整")
        return {key: user[key] for key in ("id", "familyId", "displayName", "role")}
