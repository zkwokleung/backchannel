from datetime import UTC, datetime

from sqlmodel import Field, Session, SQLModel, create_engine

from .config import get_settings


def utcnow() -> datetime:
    return datetime.now(UTC)


class Channel(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    youtube_id: str = Field(index=True, unique=True)
    handle: str | None = None
    title: str
    thumbnail: str | None = None
    added_at: datetime = Field(default_factory=utcnow)


class Video(SQLModel, table=True):
    """Cache of a channel's uploads, refreshed on demand."""

    id: int | None = Field(default=None, primary_key=True)
    youtube_id: str = Field(index=True, unique=True)
    channel_id: int = Field(foreign_key="channel.id", index=True)
    title: str
    duration_seconds: int | None = None
    thumbnail: str | None = None
    published_at: datetime | None = None
    cached_at: datetime = Field(default_factory=utcnow)


class Watchlist(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    name: str
    created_at: datetime = Field(default_factory=utcnow)


class WatchlistItem(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    watchlist_id: int = Field(foreign_key="watchlist.id", index=True)
    video_youtube_id: str = Field(index=True)
    position: int = 0
    added_at: datetime = Field(default_factory=utcnow)


class PlaybackState(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    video_youtube_id: str = Field(index=True, unique=True)
    position_seconds: float = 0.0
    completed: bool = False
    updated_at: datetime = Field(default_factory=utcnow)


_engine = None


def get_engine():
    global _engine
    if _engine is None:
        settings = get_settings()
        settings.data_dir.mkdir(parents=True, exist_ok=True)
        _engine = create_engine(
            settings.sqlite_url, connect_args={"check_same_thread": False}
        )
    return _engine


def init_db() -> None:
    SQLModel.metadata.create_all(get_engine())


def get_session():
    with Session(get_engine()) as session:
        yield session
