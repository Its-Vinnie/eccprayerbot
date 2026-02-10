import os
import sys
import requests


KJV_URL = "https://unpkg.com/kjv@1.0.0/json/verses-1769.json"


def main() -> None:
    dest = os.getenv("VERSE_DATA_PATH", "listener/data/verses-1769.json")
    os.makedirs(os.path.dirname(dest), exist_ok=True)

    resp = requests.get(KJV_URL, timeout=30)
    resp.raise_for_status()

    with open(dest, "wb") as handle:
        handle.write(resp.content)

    print(f"Downloaded KJV to {dest}")


if __name__ == "__main__":
    sys.exit(main())

