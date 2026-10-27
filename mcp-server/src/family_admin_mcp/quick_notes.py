"""Quick-note content validation and optimistic-version CRUD through the REST API."""

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StrictBool, StrictInt, StrictStr, ValidationError, field_validator, model_validator


Tag = Annotated[StrictStr, Field(min_length=1, max_length=30)]
ImageId = Annotated[StrictInt, Field(gt=0)]
Version = Annotated[StrictInt, Field(ge=0, le=2147483647)]


class QuickNoteTask(BaseModel):
    model_config = ConfigDict(extra="forbid")
    text: Annotated[StrictStr, Field(min_length=1, max_length=300)]
    done: StrictBool = False

    @field_validator("text")
    @classmethod
    def nonblank_text(cls, value):
        if not value.strip():
            raise ValueError("待办内容不能为空")
        return value


class QuickNoteContent(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: Annotated[StrictStr, Field(max_length=200)] = ""
    body: Annotated[StrictStr, Field(max_length=10000)] = ""
    tags: Annotated[list[Tag], Field(max_length=20)] = Field(default_factory=list)
    image_ids: Annotated[list[ImageId], Field(max_length=9)] = Field(default_factory=list)
    tasks: Annotated[list[QuickNoteTask], Field(max_length=100)] = Field(default_factory=list)
    color: Literal["plain", "sage", "sand"] = "plain"
    pinned: StrictBool = False

    @field_validator("title", "body")
    @classmethod
    def trim_text(cls, value):
        return value.strip()

    @field_validator("tags")
    @classmethod
    def normalize_tags(cls, values):
        if any(not value.strip() for value in values):
            raise ValueError("标签不能为空")
        return list(dict.fromkeys(value.strip() for value in values))

    @field_validator("image_ids")
    @classmethod
    def unique_images(cls, values):
        return list(dict.fromkeys(values))

    @model_validator(mode="after")
    def meaningful_content(self):
        if not (self.title or self.body or self.image_ids or self.tasks):
            raise ValueError("标题、正文、图片或待办至少填写一项")
        return self


class QuickNoteState(QuickNoteContent):
    status: Literal["ACTIVE", "ARCHIVED"]
    version: Version


class QuickNoteChanges(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: Annotated[StrictStr, Field(max_length=200)] | None = None
    body: Annotated[StrictStr, Field(max_length=10000)] | None = None
    tags: Annotated[list[Tag], Field(max_length=20)] | None = None
    image_ids: Annotated[list[ImageId], Field(max_length=9)] | None = None
    tasks: Annotated[list[QuickNoteTask], Field(max_length=100)] | None = None
    color: Literal["plain", "sage", "sand"] | None = None
    pinned: StrictBool | None = None
    status: Literal["ACTIVE", "ARCHIVED"] | None = None

    @model_validator(mode="after")
    def explicit_changes(self):
        if not self.model_fields_set:
            raise ValueError("至少指定一个修改字段")
        if any(getattr(self, key) is None for key in self.model_fields_set):
            raise ValueError("修改字段不可为null；清空请使用空字符串或空列表")
        return self


def validated(model, values):
    try:
        return model.model_validate(values)
    except ValidationError:
        # Merged values can contain private existing content; never echo validation input.
        raise ValueError("随身记字段不正确：请检查内容是否为空、长度、清单、图片编号和字段类型") from None


def api_payload(model):
    values = model.model_dump()
    values["imageIds"] = values.pop("image_ids")
    return values


def check_note_identity(note_id, version=None):
    if not isinstance(note_id, int) or isinstance(note_id, bool) or note_id <= 0:
        raise ValueError("随身记编号必须是查询得到的正整数")
    if version is not None and (not isinstance(version, int) or isinstance(version, bool) or not 0 <= version <= 2147483647):
        raise ValueError("版本必须是查询得到的非负整数")


class QuickNotesMixin:
    async def list_quick_notes(self, note_id=None, keyword=None, status=None, page=1, page_size=20):
        """Read current-family notes; pagination and search are local to the REST list."""
        if note_id is not None:
            check_note_identity(note_id)
        if not isinstance(page, int) or isinstance(page, bool) or page < 1:
            raise ValueError("页码必须是正整数")
        if not isinstance(page_size, int) or isinstance(page_size, bool) or not 1 <= page_size <= 100:
            raise ValueError("每页条数必须为1至100的整数")
        if status is not None and status not in {"ACTIVE", "ARCHIVED"}:
            raise ValueError("随身记状态只能为ACTIVE或ARCHIVED")
        if keyword is not None and (not isinstance(keyword, str) or not keyword.strip() or len(keyword) > 200):
            raise ValueError("查询关键词不能为空且不能超过200字")
        await self.client.identity()
        notes = await self.client.request("GET", "/quick-notes")
        if note_id is not None:
            notes = [note for note in notes if note["id"] == note_id]
        if status is not None:
            notes = [note for note in notes if note["status"] == status]
        if keyword is not None:
            key = keyword.strip().casefold()
            notes = [note for note in notes if key in "\n".join(
                [note["title"], note["body"], *note["tags"], *(task["text"] for task in note["tasks"])]).casefold()]
        total = len(notes)
        return {"scope": "当前家庭随身记", "notes": notes[(page - 1) * page_size:page * page_size],
                "total": total, "page": page, "page_size": page_size, "has_more": page * page_size < total}

    async def _current_quick_note(self, note_id, version):
        notes = await self.client.request("GET", "/quick-notes")
        note = next((note for note in notes if note["id"] == note_id), None)
        if note is None:
            raise ValueError("随身记不存在或不属于当前家庭，请重新查询")
        if note["version"] != version:
            raise ValueError("随身记版本已变化，请重新查询并确认后再修改或删除")
        return note

    async def create_quick_note(self, request_id, content):
        """Create ACTIVE/version-zero content with durable request deduplication."""
        payload = {**api_payload(validated(QuickNoteContent, content)), "status": "ACTIVE", "version": 0}
        user = await self.client.identity()
        return await self._write(user, request_id, "POST", "/quick-notes", payload, returns_record=True)

    async def update_quick_note(self, request_id, note_id, version, changes):
        """Merge an explicit patch only after matching the caller's read version."""
        check_note_identity(note_id, version)
        if version is None:
            raise ValueError("修改必须提供查询得到的版本")
        patch = validated(QuickNoteChanges, changes).model_dump(exclude_unset=True)
        user = await self.client.identity()

        async def prepare():
            current = await self._current_quick_note(note_id, version)
            values = {key: current[key] for key in ("title", "body", "tags", "tasks", "color", "pinned", "status", "version")}
            values["image_ids"] = current["imageIds"]
            values.update(patch)
            return api_payload(validated(QuickNoteState, values))

        return await self._write(user, request_id, "PUT", f"/quick-notes/{note_id}",
                                 {"version": version, "changes": patch}, returns_record=True, preflight=prepare)

    async def delete_quick_note(self, request_id, note_id, version):
        """Delete one selected note using its exact optimistic version."""
        check_note_identity(note_id, version)
        if version is None:
            raise ValueError("删除必须提供查询得到的版本")
        user = await self.client.identity()

        async def check_version():
            await self._current_quick_note(note_id, version)

        result = await self._write(user, request_id, "DELETE", f"/quick-notes/{note_id}?version={version}",
                                   None, preflight=check_version)
        return {**result, "status": "deleted", "note_id": note_id}
