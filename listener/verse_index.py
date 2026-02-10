import json
import os
import re
from dataclasses import dataclass
from typing import Dict, List, Optional

import numpy as np
from sentence_transformers import SentenceTransformer


@dataclass
class VerseRecord:
    ref: str
    text: str


def _clean_text(text: str) -> str:
    if not text:
        return ""
    cleaned = text.replace("\n", " ")
    cleaned = cleaned.replace("#", " ")
    cleaned = cleaned.replace("[", "").replace("]", "")
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned.strip()


def _load_json_mapping(path: str) -> Dict[str, str]:
    with open(path, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise RuntimeError("Expected JSON mapping of reference -> verse text")
    return data


def load_verses(path: str) -> List[VerseRecord]:
    if not os.path.exists(path):
        raise FileNotFoundError(f"Verse data file not found: {path}")

    records: List[VerseRecord] = []
    if path.endswith(".json"):
        data = _load_json_mapping(path)
        for ref, text in data.items():
            cleaned = _clean_text(text)
            if not ref or not cleaned:
                continue
            records.append(VerseRecord(ref=ref, text=cleaned))
    else:
        with open(path, "r", encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if not line:
                    continue
                obj = json.loads(line)
                ref = obj.get("ref")
                text = obj.get("text")
                if not ref or not text:
                    continue
                records.append(VerseRecord(ref=ref, text=_clean_text(text)))
    if not records:
        raise RuntimeError(f"No verse records found in {path}")
    return records


def _load_meta(meta_path: str) -> Optional[dict]:
    if not os.path.exists(meta_path):
        return None
    with open(meta_path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def _save_meta(meta_path: str, meta: dict) -> None:
    with open(meta_path, "w", encoding="utf-8") as handle:
        json.dump(meta, handle)


class VerseIndex:
    def __init__(self, data_path: str, embeddings_path: str, meta_path: str, model_name: str):
        self.data_path = data_path
        self.embeddings_path = embeddings_path
        self.meta_path = meta_path
        self.model_name = model_name
        self.model = SentenceTransformer(model_name)
        self.records: List[VerseRecord] = []
        self.embeddings: Optional[np.ndarray] = None
        self.norms: Optional[np.ndarray] = None

    def load(self) -> None:
        self.records = load_verses(self.data_path)
        data_mtime = os.path.getmtime(self.data_path)

        meta = _load_meta(self.meta_path)
        if meta and meta.get("source_path") == self.data_path and meta.get("source_mtime") == data_mtime and meta.get("model") == self.model_name:
            if os.path.exists(self.embeddings_path):
                self.embeddings = np.load(self.embeddings_path)
                self.norms = np.linalg.norm(self.embeddings, axis=1)
                return

        texts = [r.text for r in self.records]
        self.embeddings = self.model.encode(texts, batch_size=64, show_progress_bar=True)
        self.norms = np.linalg.norm(self.embeddings, axis=1)
        np.save(self.embeddings_path, self.embeddings)
        _save_meta(self.meta_path, {
            "source_path": self.data_path,
            "source_mtime": data_mtime,
            "model": self.model_name,
        })

    def search(self, query: str, top_k: int = 3) -> List[tuple[VerseRecord, float]]:
        if self.embeddings is None or self.norms is None:
            raise RuntimeError("VerseIndex not loaded")

        query_vec = self.model.encode([query])[0]
        query_norm = np.linalg.norm(query_vec)
        if query_norm == 0:
            return []

        scores = (self.embeddings @ query_vec) / (self.norms * query_norm)
        best_idx = np.argsort(scores)[-top_k:][::-1]
        return [(self.records[i], float(scores[i])) for i in best_idx]
