from contextlib import asynccontextmanager

from fastapi import FastAPI

from .db import init_db
from .routes import channels, videos


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    yield


def create_app() -> FastAPI:
    app = FastAPI(
        title="Backchannel Server",
        description="Self-hosted backend: channel browsing, watchlists, stream proxying.",
        version="0.1.0",
        lifespan=lifespan,
    )

    @app.get("/health", tags=["meta"])
    def health() -> dict[str, str]:
        return {"status": "ok"}

    app.include_router(channels.router)
    app.include_router(videos.router)
    return app


app = create_app()
