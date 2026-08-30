from typing import Optional
from pydantic import BaseModel


class SearchResultItem(BaseModel):
    type: str  # "user" | "device" | "vehicle"
    id: str
    title: str
    subtitle: Optional[str] = None