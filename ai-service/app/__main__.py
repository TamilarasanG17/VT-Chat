"""python -m app  ->  runs the service with the host/port from the environment."""
import uvicorn

from app.config import load_settings

if __name__ == "__main__":
    s = load_settings()
    uvicorn.run("app.main:app", host=s.host, port=s.port)
