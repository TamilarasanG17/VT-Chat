from pydantic import BaseModel, Field


class TranslateRequest(BaseModel):
    text: str = Field(..., max_length=10000)
    targetLanguage: str = Field(..., max_length=16)


class TranslateResponse(BaseModel):
    sourceLanguage: str | None
    sourceScript: str
    romanized: bool
    targetLanguage: str
    translatedText: str


class ErrorResponse(BaseModel):
    code: str
    message: str
