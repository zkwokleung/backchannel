from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Server configuration, read from environment variables (prefix BACKCHANNEL_)."""

    model_config = SettingsConfigDict(env_prefix="BACKCHANNEL_", env_file=".env", extra="ignore")

    # Auth (enforced from Phase 3 onward; empty means auth disabled for local dev)
    api_key: str = ""

    # Storage
    data_dir: Path = Path("./data")

    # Server
    host: str = "0.0.0.0"
    port: int = 8000

    # yt-dlp
    stream_url_ttl_seconds: int = 3600  # googlevideo URLs expire ~6h; stay well under
    video_list_limit: int = 100  # max uploads fetched per channel refresh

    @property
    def db_path(self) -> Path:
        return self.data_dir / "backchannel.db"

    @property
    def sqlite_url(self) -> str:
        return f"sqlite:///{self.db_path}"


@lru_cache
def get_settings() -> Settings:
    return Settings()
