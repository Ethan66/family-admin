import hashlib
import json
import sqlite3
from contextlib import contextmanager
from pathlib import Path


class Journal:
    """Conservative durable deduplication; uncertain requests remain blocked."""

    def __init__(self, path: Path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        with self._connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS request_log (
                scope TEXT NOT NULL, request_id TEXT NOT NULL, fingerprint TEXT NOT NULL,
                result TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (scope, request_id))""")
        self.path.chmod(0o600)

    @contextmanager
    def _connect(self):
        db = sqlite3.connect(self.path, timeout=5)
        try:
            with db:
                yield db
        finally:
            db.close()

    def reserve(self, scope, request_id, operation, payload):
        if not isinstance(request_id, str) or not request_id.strip() or len(request_id) > 200:
            raise ValueError("请求编号不能为空且不能超过 200 字；同一消息的同一操作须沿用相同编号")
        fingerprint = hashlib.sha256(json.dumps([operation, payload], ensure_ascii=False, sort_keys=True).encode()).hexdigest()
        with self._connect() as db:
            try:
                db.execute("INSERT INTO request_log(scope, request_id, fingerprint) VALUES (?, ?, ?)",
                           (scope, request_id, fingerprint))
                return None
            except sqlite3.IntegrityError:
                previous = db.execute("SELECT fingerprint, result FROM request_log WHERE scope=? AND request_id=?",
                                      (scope, request_id)).fetchone()
                if previous[0] != fingerprint:
                    raise ValueError("请求编号已用于不同内容，不能覆盖原操作") from None
                if previous[1] is None:
                    raise ValueError("该请求正在处理或结果不确定，请先查询记录核实，勿换编号重试") from None
                return json.loads(previous[1])

    def finish(self, scope, request_id, result):
        with self._connect() as db:
            db.execute("UPDATE request_log SET result=? WHERE scope=? AND request_id=?",
                       (json.dumps(result, ensure_ascii=False), scope, request_id))

    def release(self, scope, request_id):
        with self._connect() as db:
            db.execute("DELETE FROM request_log WHERE scope=? AND request_id=? AND result IS NULL", (scope, request_id))
