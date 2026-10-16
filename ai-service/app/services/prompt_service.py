from app.config import Settings


def system_prompt(settings: Settings) -> str:
    """The system prompt lives ONLY here (configured on the server). Nothing in a request can replace it."""
    return settings.system_prompt
