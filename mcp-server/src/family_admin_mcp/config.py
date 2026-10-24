import os
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlsplit


@dataclass(frozen=True)
class Settings:
    api_url: str
    username: str
    password: str = field(repr=False)
    state_path: Path
    timeout: float = 15.0
    family_id: int | None = None
    default_member_id: int | None = None
    course_default_member_id: int | None = None

    def __post_init__(self):
        try:
            url = urlsplit(self.api_url)
            valid = (url.scheme in {"http", "https"} and url.hostname and not url.username
                     and not url.password and not url.query and not url.fragment
                     and url.path.rstrip("/").endswith("/api"))
            _ = url.port
        except ValueError:
            valid = False
        if not valid:
            raise ValueError("FAMILY_API_URL 必须是以 /api 结尾的 HTTP(S) 地址，不能包含凭证、查询或片段")
        if not self.username.strip() or not self.password:
            raise ValueError("必须设置 FAMILY_USERNAME 和 FAMILY_PASSWORD")
        if not 1 <= self.timeout <= 120:
            raise ValueError("FAMILY_TIMEOUT_SECONDS 必须在 1 到 120 之间")
        for label, value in (("FAMILY_ID", self.family_id), ("FAMILY_DEFAULT_MEMBER_ID", self.default_member_id),
                             ("FAMILY_COURSE_DEFAULT_MEMBER_ID", self.course_default_member_id)):
            if value is not None and (not isinstance(value, int) or isinstance(value, bool) or value <= 0):
                raise ValueError(f"{label} 必须是正整数")
        object.__setattr__(self, "api_url", self.api_url.rstrip("/"))
        object.__setattr__(self, "state_path", Path(self.state_path).expanduser().resolve())

    @classmethod
    def from_env(cls):
        required = ("FAMILY_API_URL", "FAMILY_USERNAME", "FAMILY_PASSWORD")
        for key in required:
            if not os.environ.get(key):
                raise ValueError(f"必须设置 {key}")
        try:
            timeout = float(os.environ.get("FAMILY_TIMEOUT_SECONDS", "15"))
        except ValueError:
            raise ValueError("FAMILY_TIMEOUT_SECONDS 必须是数字") from None
        def optional_id(key):
            value = os.environ.get(key)
            try:
                return int(value) if value else None
            except ValueError:
                raise ValueError(f"{key} 必须是正整数") from None
        return cls(*(os.environ[key] for key in required),
                   Path(os.environ.get("FAMILY_STATE_PATH", "~/.local/state/family-admin-mcp/requests.sqlite3")), timeout,
                   optional_id("FAMILY_ID"), optional_id("FAMILY_DEFAULT_MEMBER_ID"), optional_id("FAMILY_COURSE_DEFAULT_MEMBER_ID"))
