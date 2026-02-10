import time
import requests


class TelegramSender:
    def __init__(self, bot_token: str, chat_id: str):
        self.bot_token = bot_token
        self.chat_id = chat_id
        self.api_base = f"https://api.telegram.org/bot{bot_token}"

    def send(self, text: str) -> None:
        payload = {
            "chat_id": self.chat_id,
            "text": text,
            "parse_mode": "HTML",
            "disable_web_page_preview": True,
        }
        resp = requests.post(f"{self.api_base}/sendMessage", json=payload, timeout=10)
        if resp.status_code != 200:
            raise RuntimeError(f"Telegram send failed: {resp.status_code} {resp.text}")

    def send_chunks(self, text: str, max_len: int = 4000) -> None:
        if len(text) <= max_len:
            self.send(text)
            return

        remaining = text
        while len(remaining) > max_len:
            split_at = remaining.rfind("\n", 0, max_len)
            if split_at < max_len * 0.7:
                split_at = remaining.rfind(" ", 0, max_len)
            if split_at < max_len * 0.5:
                split_at = max_len

            chunk = remaining[:split_at].strip()
            if chunk:
                self.send(chunk)
                time.sleep(0.2)
            remaining = remaining[split_at:].strip()

        if remaining:
            self.send(remaining)

