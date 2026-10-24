import pytest

from family_admin_mcp.config import Settings


def test_missing_credentials_fail_without_echoing_secrets(monkeypatch):
    for key in ("FAMILY_API_URL", "FAMILY_USERNAME", "FAMILY_PASSWORD"):
        monkeypatch.delenv(key, raising=False)
    with pytest.raises(ValueError, match="FAMILY_API_URL"):
        Settings.from_env()


@pytest.mark.parametrize("url", ["file:///api", "http://user:secret@host/api", "http://host/api?token=secret", "http://host/api#secret"])
def test_invalid_backend_url_is_rejected(url, tmp_path):
    with pytest.raises(ValueError) as error:
        Settings(url, "user", "password", tmp_path / "state.sqlite3")
    assert "secret" not in str(error.value)


def test_secrets_not_in_settings_representation(tmp_path):
    settings = Settings("http://localhost/api", "user", "private-password", tmp_path / "state.sqlite3")
    assert "private-password" not in repr(settings)


@pytest.mark.parametrize("member_id", [0, -1, True])
def test_course_default_member_must_be_positive_integer(tmp_path, member_id):
    with pytest.raises(ValueError, match="FAMILY_COURSE_DEFAULT_MEMBER_ID"):
        Settings("http://localhost/api", "user", "password", tmp_path / "state.sqlite3", course_default_member_id=member_id)
