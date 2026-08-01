from typing import Any

from fastapi import APIRouter, HTTPException
from sqlmodel import col, select

from ..db import Channel, SessionDep, Video
from ..ytdlp_service import YtdlpError, get_video_info

router = APIRouter(tags=["videos"])


@router.get("/channels/{channel_id}/videos", response_model=list[Video])
def list_videos_for_channel(
    channel_id: int, session: SessionDep
) -> list[Video]:
    """Cached uploads for a saved channel, newest first."""
    if session.get(Channel, channel_id) is None:
        raise HTTPException(status_code=404, detail="Channel not found")
    videos = session.exec(
        select(Video)
        .where(Video.channel_id == channel_id)
        .order_by(col(Video.published_at).desc(), col(Video.id))
    ).all()
    return list(videos)


@router.get("/videos/{video_youtube_id}")
def video_info(video_youtube_id: str) -> dict[str, Any]:
    """Full live metadata for one video (fetched from YouTube, not the cache)."""
    try:
        return get_video_info(video_youtube_id)
    except YtdlpError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
