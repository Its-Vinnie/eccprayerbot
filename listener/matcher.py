from dataclasses import dataclass
from typing import List, Optional

from listener.ref_parser import BibleReference, extract_translation, parse_reference
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from listener.verse_index import VerseIndex, VerseRecord


@dataclass
class MatchResult:
    reference: Optional[BibleReference]
    translation: str
    candidates: List[tuple["VerseRecord", float]]


class VerseMatcher:
    def __init__(self, verse_index: Optional["VerseIndex"], default_translation: str, min_score: float, strong_score: float, top_k: int):
        self.verse_index = verse_index
        self.default_translation = default_translation
        self.min_score = min_score
        self.strong_score = strong_score
        self.top_k = top_k

    def detect(self, transcript: str) -> Optional[MatchResult]:
        if not transcript or transcript.strip() == "":
            return None

        explicit = parse_reference(transcript)
        translation = extract_translation(transcript) or self.default_translation

        if explicit:
            if explicit.translation:
                translation = explicit.translation
            return MatchResult(reference=explicit, translation=translation, candidates=[])

        if self.verse_index is None:
            return None

        candidates = self.verse_index.search(transcript, top_k=self.top_k)
        if not candidates:
            return None

        if candidates[0][1] < self.min_score:
            return None

        return MatchResult(reference=None, translation=translation, candidates=candidates)

    def is_strong(self, score: float) -> bool:
        return score >= self.strong_score
