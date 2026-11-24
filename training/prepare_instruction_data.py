#!/usr/bin/env python3
"""Convert a licensed Hugging Face Turkish chat dataset to clean JSONL messages.

Default source: cisimcik/turkish-chat-max-25k (Apache-2.0, per its dataset card).
Only tr_only prompts without tool interactions are included by default.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import logging
from pathlib import Path
from typing import Any

from datasets import load_dataset

LOG = logging.getLogger("anon_prepare_sft")
ALLOWED_ROLES = {"system", "user", "assistant"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-id", default="cisimcik/turkish-chat-max-25k")
    parser.add_argument("--config", default="all")
    parser.add_argument("--split", default="train")
    parser.add_argument("--output", default="./turkish_chat_train.jsonl")
    parser.add_argument("--max-samples", type=int, default=20000)
    parser.add_argument("--language-mode", default="tr_only")
    parser.add_argument("--prompt-language", default="tr")
    parser.add_argument("--include-tools", action="store_true")
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def normalize_messages(value: Any) -> list[dict[str, str]] | None:
    if not isinstance(value, list):
        return None
    messages: list[dict[str, str]] = []
    for item in value:
        if not isinstance(item, dict):
            return None
        role = str(item.get("role", "")).strip().lower()
        content = item.get("content")
        if role not in ALLOWED_ROLES or not isinstance(content, str):
            return None
        content = " ".join(content.replace("\x00", " ").split()).strip()
        if not content:
            continue
        messages.append({"role": role, "content": content})

    roles = {message["role"] for message in messages}
    if "user" not in roles or "assistant" not in roles:
        return None
    return messages


def main() -> None:
    args = parse_args()
    if args.max_samples < 10:
        raise ValueError("--max-samples must be at least 10")

    logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
    LOG.info("Loading %s (%s / %s)", args.dataset_id, args.config, args.split)
    dataset = load_dataset(args.dataset_id, args.config, split=args.split)
    dataset = dataset.shuffle(seed=args.seed)

    output_path = Path(args.output).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    seen: set[str] = set()
    written = 0
    skipped_language = 0
    skipped_tools = 0
    skipped_format = 0

    with output_path.open("w", encoding="utf-8") as output:
        for row in dataset:
            language_mode = str(row.get("language_mode", "")).strip().lower()
            prompt_language = str(row.get("prompt_language", "")).strip().lower()
            if args.language_mode and language_mode != args.language_mode.lower():
                skipped_language += 1
                continue
            if args.prompt_language and prompt_language != args.prompt_language.lower():
                skipped_language += 1
                continue
            if not args.include_tools and bool(row.get("has_tools", False)):
                skipped_tools += 1
                continue

            messages = normalize_messages(row.get("messages"))
            if not messages:
                skipped_format += 1
                continue

            canonical = json.dumps(messages, ensure_ascii=False, sort_keys=True)
            digest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
            if digest in seen:
                continue
            seen.add(digest)

            record = {
                "messages": messages,
                "source_dataset": args.dataset_id,
                "dataset_license": "Apache-2.0",
            }
            output.write(json.dumps(record, ensure_ascii=False) + "\n")
            written += 1
            if written >= args.max_samples:
                break

    if written < 10:
        raise RuntimeError(
            f"Only {written} usable conversations were written. "
            "Check the dataset schema and language filters."
        )

    metadata = {
        "dataset_id": args.dataset_id,
        "config": args.config,
        "split": args.split,
        "license_declared_by_dataset_card": "Apache-2.0",
        "attribution": "cisimcik/turkish-chat-max-25k",
        "language_mode": args.language_mode,
        "prompt_language": args.prompt_language,
        "include_tools": args.include_tools,
        "records_written": written,
        "skipped_language": skipped_language,
        "skipped_tools": skipped_tools,
        "skipped_invalid_format": skipped_format,
        "deduplication": "SHA-256 over normalized messages",
    }
    metadata_path = output_path.with_suffix(output_path.suffix + ".metadata.json")
    metadata_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    LOG.info("Wrote %s conversations to %s", written, output_path)
    LOG.info("Metadata saved to %s", metadata_path)


if __name__ == "__main__":
    main()
