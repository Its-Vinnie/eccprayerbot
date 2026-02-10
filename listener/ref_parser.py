import re
from dataclasses import dataclass
from typing import Optional


BIBLE_REF_PATTERN = re.compile(
    r"(?i)(\d?\s*[a-z]+(?:\s+[a-z]+)?)\s+(\d+)(?::(\d+)(?:-(\d+))?)?(?:\s+([a-z0-9]{2,10}))?",
    re.IGNORECASE,
)

BOOK_ABBREVIATIONS = {
    # Old Testament
    "gen": "Genesis",
    "exod": "Exodus",
    "ex": "Exodus",
    "lev": "Leviticus",
    "num": "Numbers",
    "deut": "Deuteronomy",
    "josh": "Joshua",
    "judg": "Judges",
    "ruth": "Ruth",
    "1sam": "1 Samuel",
    "2sam": "2 Samuel",
    "1kgs": "1 Kings",
    "2kgs": "2 Kings",
    "1chr": "1 Chronicles",
    "2chr": "2 Chronicles",
    "ezra": "Ezra",
    "neh": "Nehemiah",
    "esth": "Esther",
    "job": "Job",
    "ps": "Psalms",
    "psa": "Psalms",
    "psalm": "Psalms",
    "prov": "Proverbs",
    "eccl": "Ecclesiastes",
    "song": "Song of Solomon",
    "isa": "Isaiah",
    "jer": "Jeremiah",
    "lam": "Lamentations",
    "ezek": "Ezekiel",
    "dan": "Daniel",
    "hos": "Hosea",
    "joel": "Joel",
    "amos": "Amos",
    "obad": "Obadiah",
    "jonah": "Jonah",
    "mic": "Micah",
    "nah": "Nahum",
    "hab": "Habakkuk",
    "zeph": "Zephaniah",
    "hag": "Haggai",
    "zech": "Zechariah",
    "mal": "Malachi",
    # New Testament
    "matt": "Matthew",
    "mt": "Matthew",
    "mark": "Mark",
    "mk": "Mark",
    "luke": "Luke",
    "lk": "Luke",
    "john": "John",
    "jn": "John",
    "acts": "Acts",
    "rom": "Romans",
    "1cor": "1 Corinthians",
    "2cor": "2 Corinthians",
    "gal": "Galatians",
    "eph": "Ephesians",
    "phil": "Philippians",
    "col": "Colossians",
    "1thess": "1 Thessalonians",
    "2thess": "2 Thessalonians",
    "1tim": "1 Timothy",
    "2tim": "2 Timothy",
    "titus": "Titus",
    "phlm": "Philemon",
    "heb": "Hebrews",
    "jas": "James",
    "1pet": "1 Peter",
    "2pet": "2 Peter",
    "1john": "1 John",
    "2john": "2 John",
    "3john": "3 John",
    "jude": "Jude",
    "rev": "Revelation",
}

TRANSLATIONS = {
    "KJV",
    "ASV",
    "WEB",
    "WEBBE",
    "BBE",
    "FBV",
    "RSV",
    "GNT",
    "DRA",
    "GNV",
    "TCNT",
    "RV",
    "NIV",
    "NKJV",
    "NLT",
    "ESV",
    "AMP",
    "AMPC",
    "MSG",
    "TPT",
    "EASY",
    "CSB",
}


@dataclass
class BibleReference:
    book: str
    chapter: int
    verse_start: Optional[int]
    verse_end: Optional[int]
    translation: Optional[str]

    def display(self) -> str:
        base = f"{self.book} {self.chapter}"
        if self.verse_start is not None:
            base += f":{self.verse_start}"
            if self.verse_end and self.verse_end != self.verse_start:
                base += f"-{self.verse_end}"
        return base


def normalize_book_name(book_raw: str) -> str:
    normalized = re.sub(r"\s+", "", book_raw.strip().lower())
    full_name = BOOK_ABBREVIATIONS.get(normalized)
    if full_name:
        return full_name

    words = book_raw.strip().split()
    return " ".join(w.capitalize() for w in words if w)


def parse_reference(text: str) -> Optional[BibleReference]:
    if not text or not text.strip():
        return None

    text = re.sub(r"@\w+\s*", "", text).strip()
    match = BIBLE_REF_PATTERN.search(text)
    if not match:
        return None

    book_raw = match.group(1).strip()
    chapter_str = match.group(2)
    verse_start = match.group(3)
    verse_end = match.group(4)
    translation = match.group(5)

    book = normalize_book_name(book_raw)
    translation = translation.upper() if translation else None

    ref = BibleReference(
        book=book,
        chapter=int(chapter_str),
        verse_start=int(verse_start) if verse_start else None,
        verse_end=int(verse_end) if verse_end else None,
        translation=translation,
    )

    if ref.chapter <= 0:
        return None
    if ref.verse_start is not None and ref.verse_start <= 0:
        return None
    if ref.verse_end is not None and ref.verse_end < ref.verse_start:
        return None

    return ref


def extract_translation(text: str) -> Optional[str]:
    if not text:
        return None
    for token in re.findall(r"\b[A-Za-z0-9]{2,10}\b", text.upper()):
        if token in TRANSLATIONS:
            return token
    return None
