import html
import re
from dataclasses import dataclass
from typing import Optional

import requests

from listener.ref_parser import BibleReference


VERSION_MAPPINGS = {
    # API.Bible IDs
    "KJV": "de4e12af7f28f599-01",
    "ASV": "06125adad2d5898a-01",
    "WEB": "9879dbb7cfe39e4d-04",
    "BBE": "7142879509583d59-01",
    "WEBBE": "7142879509583d59-01",
    "NIV": "78a9f6124f344018-01",
    "CSB": "a556c5305ee15c3f-01",
    "NKJV": "63097d2a0a2f7db3-01",
    "NLT": "d6e14a625393b4da-01",
    "AMP": "a81b73293d3080c9-01",
    "MSG": "6f11a7de016f942e-01",
}

YOUVERSION_MAPPINGS = {
    "EASY": "2079",
    "TPT": "1849",
    "AMP": "1588",
    "NLT": "116",
    "ESV": "59",
    "MSG": "97",
    "NKJV": "114",
}

BOOK_IDS = {
    "genesis": "GEN",
    "exodus": "EXO",
    "leviticus": "LEV",
    "numbers": "NUM",
    "deuteronomy": "DEU",
    "joshua": "JOS",
    "judges": "JDG",
    "ruth": "RUT",
    "1 samuel": "1SA",
    "2 samuel": "2SA",
    "1 kings": "1KI",
    "2 kings": "2KI",
    "1 chronicles": "1CH",
    "2 chronicles": "2CH",
    "ezra": "EZR",
    "nehemiah": "NEH",
    "esther": "EST",
    "job": "JOB",
    "psalms": "PSA",
    "proverbs": "PRO",
    "ecclesiastes": "ECC",
    "song of solomon": "SNG",
    "isaiah": "ISA",
    "jeremiah": "JER",
    "lamentations": "LAM",
    "ezekiel": "EZK",
    "daniel": "DAN",
    "hosea": "HOS",
    "joel": "JOL",
    "amos": "AMO",
    "obadiah": "OBA",
    "jonah": "JON",
    "micah": "MIC",
    "nahum": "NAM",
    "habakkuk": "HAB",
    "zephaniah": "ZEP",
    "haggai": "HAG",
    "zechariah": "ZEC",
    "malachi": "MAL",
    "matthew": "MAT",
    "mark": "MRK",
    "luke": "LUK",
    "john": "JHN",
    "acts": "ACT",
    "romans": "ROM",
    "1 corinthians": "1CO",
    "2 corinthians": "2CO",
    "galatians": "GAL",
    "ephesians": "EPH",
    "philippians": "PHP",
    "colossians": "COL",
    "1 thessalonians": "1TH",
    "2 thessalonians": "2TH",
    "1 timothy": "1TI",
    "2 timothy": "2TI",
    "titus": "TIT",
    "philemon": "PHM",
    "hebrews": "HEB",
    "james": "JAS",
    "1 peter": "1PE",
    "2 peter": "2PE",
    "1 john": "1JN",
    "2 john": "2JN",
    "3 john": "3JN",
    "jude": "JUD",
    "revelation": "REV",
}


@dataclass
class VerseText:
    reference: str
    text: str
    version_name: str


class BibleFetcher:
    def __init__(self, bible_base: str, bible_key: str, youversion_base: str, youversion_key: str):
        self.bible_base = bible_base.rstrip("/")
        self.bible_key = bible_key
        self.youversion_base = youversion_base.rstrip("/")
        self.youversion_key = youversion_key

    def fetch(self, ref: BibleReference, translation: str) -> Optional[VerseText]:
        translation = translation.upper() if translation else "KJV"

        if translation in VERSION_MAPPINGS:
            return self._fetch_api_bible(ref, translation)

        if translation in YOUVERSION_MAPPINGS:
            return self._fetch_youversion(ref, translation)

        # fallback to KJV
        return self._fetch_api_bible(ref, "KJV")

    def _fetch_api_bible(self, ref: BibleReference, translation: str) -> Optional[VerseText]:
        bible_id = VERSION_MAPPINGS.get(translation)
        passage_id = build_passage_id(ref)
        if not bible_id or not passage_id:
            return None

        headers = {"api-key": self.bible_key, "Accept": "application/json"}
        url = f"{self.bible_base}/bibles/{bible_id}/passages/{passage_id}"
        params = {"content-type": "text", "include-verse-numbers": "true"}
        resp = requests.get(url, headers=headers, params=params, timeout=10)
        if resp.status_code != 200:
            return None

        data = resp.json().get("data", {})
        text = data.get("content", "").strip()
        reference = data.get("reference", ref.display())

        # normalize verse numbers
        text = re.sub(r"\[(\d+)\]", r"<b>\1</b> ", text)

        version_name = translation
        return VerseText(reference=reference, text=text, version_name=version_name)

    def _fetch_youversion(self, ref: BibleReference, translation: str) -> Optional[VerseText]:
        version_id = YOUVERSION_MAPPINGS.get(translation)
        if not version_id:
            return None

        headers = {
            "X-YouVersion-Developer-Token": self.youversion_key,
            "X-YVP-App-Key": self.youversion_key,
            "Accept": "application/json",
        }

        reference_id = build_youversion_ref(ref)
        url = f"{self.youversion_base}/v1/bibles/{version_id}/passages/{reference_id}"
        resp = requests.get(url, headers=headers, timeout=10)
        if resp.status_code != 200:
            return None

        data = resp.json()
        text = data.get("content", "").strip()
        reference = data.get("reference", ref.display())
        text = re.sub(r"\[(\d+)\]", r"<b>\1</b> ", text)

        return VerseText(reference=reference, text=text, version_name=translation)


def build_passage_id(ref: BibleReference) -> Optional[str]:
    book_id = BOOK_IDS.get(ref.book.lower())
    if not book_id:
        return None

    if ref.verse_start is None:
        return f"{book_id}.{ref.chapter}"
    if ref.verse_end is None or ref.verse_end == ref.verse_start:
        return f"{book_id}.{ref.chapter}.{ref.verse_start}"
    return f"{book_id}.{ref.chapter}.{ref.verse_start}-{book_id}.{ref.chapter}.{ref.verse_end}"


def build_youversion_ref(ref: BibleReference) -> str:
    book_id = BOOK_IDS.get(ref.book.lower(), ref.book[:3].upper())
    base = f"{book_id}.{ref.chapter}"
    if ref.verse_start is None:
        return base
    if ref.verse_end:
        return f"{base}.{ref.verse_start}-{ref.verse_end}"
    return f"{base}.{ref.verse_start}"


def format_for_telegram(verse: VerseText) -> str:
    header = f"<b>{html.escape(verse.reference)} ({html.escape(verse.version_name)})</b>\n\n"
    body = verse.text.strip()
    body = body.replace("\r\n", "\n").replace("\r", "\n")
    body = re.sub(r"\n{2,}", "\n\n", body)
    body = re.sub(r"[ \t\x0B\f\r]{2,}", " ", body)
    body = re.sub(r" ?\n ?", "\n", body)
    return header + body

