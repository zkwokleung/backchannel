"""yt-dlp used as a library: channel resolution, upload listing, video info.

All functions are synchronous (yt-dlp is blocking); routes run them in the
FastAPI threadpool via normal `def` endpoints.
"""

from datetime import UTC, datetime
from typing import Any

import yt_dlp

from .config import get_settings


class YtdlpError(Exception):
    """Raised when yt-dlp cannot resolve the requested resource."""


def _flat_opts(playlist_end: int | None = None) -> dict[str, Any]:
    opts: dict[str, Any] = {
        "quiet": True,
        "no_warnings": True,
        "extract_flat": "in_playlist",
        "skip_download": True,
    }
    if playlist_end:
        opts["playlistend"] = playlist_end
    return opts


def _normalize_channel_url(handle_or_url: str) -> str:
    """Accept @handle, handle, channel ID (UC...), or a full channel URL."""
    value = handle_or_url.strip()
    if value.startswith(("http://", "https://")):
        return value.split("?")[0].rstrip("/")
    if value.startswith("UC") and len(value) == 24:
        return f"https://www.youtube.com/channel/{value}"
    if not value.startswith("@"):
        value = f"@{value}"
    return f"https://www.youtube.com/{value}"


def resolve_channel(handle_or_url: str) -> dict[str, Any]:
    """Resolve a channel handle/URL to metadata: youtube_id, handle, title, thumbnail."""
    url = _normalize_channel_url(handle_or_url) + "/videos"
    try:
        with yt_dlp.YoutubeDL(_flat_opts(playlist_end=1)) as ydl:
            info = ydl.extract_info(url, download=False)
    except yt_dlp.utils.DownloadError as exc:
        raise YtdlpError(f"Could not resolve channel {handle_or_url!r}: {exc}") from exc

    thumbnails = info.get("thumbnails") or []
    avatar = next(
        (t["url"] for t in thumbnails if "avatar" in (t.get("id") or "")),
        thumbnails[-1]["url"] if thumbnails else None,
    )
    return {
        "youtube_id": info["channel_id"],
        "handle": info.get("uploader_id"),
        "title": info.get("channel") or info.get("title") or handle_or_url,
        "thumbnail": avatar,
    }


def list_channel_videos(channel_youtube_id: str, limit: int | None = None) -> list[dict[str, Any]]:
    """List a channel's uploads (newest first) using flat extraction — fast, no per-video fetch."""
    settings = get_settings()
    limit = limit or settings.video_list_limit
    url = f"https://www.youtube.com/channel/{channel_youtube_id}/videos"
    try:
        with yt_dlp.YoutubeDL(_flat_opts(playlist_end=limit)) as ydl:
            info = ydl.extract_info(url, download=False)
    except yt_dlp.utils.DownloadError as exc:
        raise YtdlpError(f"Could not list videos for {channel_youtube_id!r}: {exc}") from exc

    videos = []
    for entry in info.get("entries") or []:
        if not entry or entry.get("id") is None:
            continue
        thumbnails = entry.get("thumbnails") or []
        videos.append(
            {
                "youtube_id": entry["id"],
                "title": entry.get("title") or entry["id"],
                "duration_seconds": int(entry["duration"]) if entry.get("duration") else None,
                "thumbnail": thumbnails[-1]["url"] if thumbnails else None,
                # flat extraction rarely includes dates; filled by get_video_info when needed
                "published_at": _parse_upload_date(entry.get("upload_date")),
            }
        )
    return videos


def get_video_info(video_youtube_id: str) -> dict[str, Any]:
    """Full metadata for a single video (blocking full extraction)."""
    opts = {"quiet": True, "no_warnings": True, "skip_download": True}
    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(
                f"https://www.youtube.com/watch?v={video_youtube_id}", download=False
            )
    except yt_dlp.utils.DownloadError as exc:
        raise YtdlpError(f"Could not fetch video {video_youtube_id!r}: {exc}") from exc

    return {
        "youtube_id": info["id"],
        "title": info.get("title"),
        "description": info.get("description"),
        "duration_seconds": int(info["duration"]) if info.get("duration") else None,
        "thumbnail": info.get("thumbnail"),
        "published_at": _parse_upload_date(info.get("upload_date")),
        "channel_youtube_id": info.get("channel_id"),
        "channel_title": info.get("channel"),
        "view_count": info.get("view_count"),
    }


def _parse_upload_date(upload_date: str | None) -> datetime | None:
    if not upload_date:
        return None
    try:
        return datetime.strptime(upload_date, "%Y%m%d").replace(tzinfo=UTC)
    except ValueError:
        return None
