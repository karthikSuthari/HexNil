"""Run HexNil FastAPI ingestion server."""

import os
import uvicorn

HOST = os.getenv("HEXNIL_HOST", "0.0.0.0")
PORT = int(os.getenv("HEXNIL_PORT", "8000"))
RELOAD = os.getenv("HEXNIL_DEBUG", "false").lower() == "true"

if __name__ == "__main__":
    uvicorn.run(
        "backend.hexnil_fastapi_server:app",
        host=HOST,
        port=PORT,
        reload=RELOAD,
        log_level="info",
        access_log=True,
    )
