from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from sqlmodel import Session, col, select

from ..db import Channel, SessionDep, Video, utcnow
from ..ytdlp_service import YtdlpError, list_channel_videos, resolve_channel

router = APIRouter(prefix="/channels", tags=["channels"])


class ChannelCreate(BaseModel):
    handle_or_url: str


def _refresh_channel_videos(channel: Channel, session: Session) -> int:
    """Re-fetch a channel's uploads into the Video cache. Returns count cached."""
    try:
        uploads = list_channel_videos(channel.youtube_id)
    except YtdlpError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    now = utcnow()
    existing = {
        v.youtube_id: v
        for v in session.exec(select(Video).where(Video.channel_id == channel.id)).all()
    }
    for item in uploads:
        video = existing.get(item["youtube_id"])
        if video is None:
            video = Video(channel_id=channel.id, **item)
        else:
            video.title = item["title"]
            video.duration_seconds = item["duration_seconds"] or video.duration_seconds
            video.thumbnail = item["thumbnail"] or video.thumbnail
            video.published_at = item["published_at"] or video.published_at
        video.cached_at = now
        session.add(video)
    session.commit()
    return len(uploads)


@router.post("", response_model=Channel, status_code=201)
def add_channel(payload: ChannelCreate, session: SessionDep) -> Channel:
    """Resolve a handle/URL, store the channel, and cache its uploads."""
    try:
        meta = resolve_channel(payload.handle_or_url)
    except YtdlpError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    existing = session.exec(
        select(Channel).where(Channel.youtube_id == meta["youtube_id"])
    ).first()
    if existing:
        raise HTTPException(status_code=409, detail="Channel already saved")

    channel = Channel(**meta)
    session.add(channel)
    session.commit()
    session.refresh(channel)
    _refresh_channel_videos(channel, session)
    session.refresh(channel)  # commit in refresh expires attributes; reload before serializing
    return channel


@router.get("", response_model=list[Channel])
def list_channels(session: SessionDep) -> list[Channel]:
    return list(session.exec(select(Channel).order_by(col(Channel.added_at))).all())


@router.get("/{channel_id}", response_model=Channel)
def get_channel(channel_id: int, session: SessionDep) -> Channel:
    channel = session.get(Channel, channel_id)
    if channel is None:
        raise HTTPException(status_code=404, detail="Channel not found")
    return channel


@router.delete("/{channel_id}", status_code=204)
def delete_channel(channel_id: int, session: SessionDep) -> None:
    channel = session.get(Channel, channel_id)
    if channel is None:
        raise HTTPException(status_code=404, detail="Channel not found")
    for video in session.exec(select(Video).where(Video.channel_id == channel_id)).all():
        session.delete(video)
    session.delete(channel)
    session.commit()


@router.post("/{channel_id}/refresh")
def refresh_channel(channel_id: int, session: SessionDep) -> dict[str, int]:
    channel = session.get(Channel, channel_id)
    if channel is None:
        raise HTTPException(status_code=404, detail="Channel not found")
    count = _refresh_channel_videos(channel, session)
    return {"cached_videos": count}
