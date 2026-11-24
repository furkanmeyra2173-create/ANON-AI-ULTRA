#!/usr/bin/env python3
"""Instruction-tune an ANON base checkpoint using owned/licensed JSONL data."""

from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path
from typing import Any

import torch
from datasets import Dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    Trainer,
    TrainingArguments,
    set_seed,
)

LOG = logging.getLogger("anon_sft")
ROLE_TOKENS = {
    "system": "<|system|>",
    "user": "<|user|>",
    "assistant": "<|assistant|>",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-model", required=True)
    parser.add_argument("--dataset-jsonl", required=True)
    parser.add_argument("--output-dir", default="./anon-ai-ultra-chat")
    parser.add_argument("--max-samples", type=int, default=50000)
    parser.add_argument("--max-length", type=int, default=512)
    parser.add_argument("--max-steps", type=int, default=500)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def read_messages(path: Path, limit: int) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"Invalid JSON on line {line_number}: {exc}") from exc

            messages = record.get("messages")
            if not isinstance(messages, list) or not messages:
                continue
            if not any(
                isinstance(item, dict)
                and item.get("role") == "assistant"
                and isinstance(item.get("content"), str)
                and item["content"].strip()
                for item in messages
            ):
                continue
            records.append({"messages": messages})
            if len(records) >= limit:
                break

    if len(records) < 10:
        raise RuntimeError(
            f"Only {len(records)} valid conversations found. "
            "Provide at least 10 JSONL rows with a messages array."
        )
    return records


def prepare_example(
    messages: list[dict[str, Any]],
    tokenizer,
    max_length: int,
) -> dict[str, list[int]] | None:
    input_ids: list[int] = []
    labels: list[int] = []
    eos_id = tokenizer.eos_token_id

    for item in messages:
        if not isinstance(item, dict):
            continue
        role = item.get("role")
        content = item.get("content")
        if role not in ROLE_TOKENS or not isinstance(content, str) or not content.strip():
            continue

        marker_id = tokenizer.convert_tokens_to_ids(ROLE_TOKENS[role])
        content_ids = tokenizer.encode(content.strip(), add_special_tokens=False)
        message_ids = [marker_id] + content_ids + [eos_id]
        input_ids.extend(message_ids)

        if role == "assistant":
            labels.extend([-100] + content_ids + [eos_id])
        else:
            labels.extend([-100] * len(message_ids))

    if not input_ids or not any(label != -100 for label in labels):
        return None
    if len(input_ids) > max_length:
        return None

    return {
        "input_ids": input_ids,
        "attention_mask": [1] * len(input_ids),
        "labels": labels,
    }


def main() -> None:
    args = parse_args()
    if args.max_length < 64 or args.max_length > 2048:
        raise ValueError("--max-length must be between 64 and 2048")
    if args.max_steps < 1:
        raise ValueError("--max-steps must be at least 1")

    logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
    set_seed(args.seed)

    base_dir = Path(args.base_model).resolve()
    data_path = Path(args.dataset_jsonl).resolve()
    output_dir = Path(args.output_dir).resolve()
    if not base_dir.exists():
        raise FileNotFoundError(f"Base model folder not found: {base_dir}")
    if not data_path.is_file():
        raise FileNotFoundError(f"Instruction dataset not found: {data_path}")

    tokenizer = AutoTokenizer.from_pretrained(str(base_dir))
    new_tokens = [
        token for token in ROLE_TOKENS.values()
        if token not in tokenizer.get_vocab()
    ]
    if new_tokens:
        tokenizer.add_special_tokens({"additional_special_tokens": new_tokens})

    model = AutoModelForCausalLM.from_pretrained(str(base_dir))
    if new_tokens:
        model.resize_token_embeddings(len(tokenizer))
    model.config.use_cache = False

    records = read_messages(data_path, args.max_samples)
    prepared = []
    for record in records:
        item = prepare_example(record["messages"], tokenizer, args.max_length)
        if item is not None:
            prepared.append(item)

    if len(prepared) < 10:
        raise RuntimeError(
            "Fewer than 10 conversations fit the selected context length. "
            "Clean the data or increase --max-length."
        )

    train_dataset = Dataset.from_list(prepared)
    LOG.info("Prepared %s instruction conversations", len(train_dataset))

    def collate(features: list[dict[str, list[int]]]) -> dict[str, torch.Tensor]:
        max_len = max(len(feature["input_ids"]) for feature in features)
        batch_input, batch_mask, batch_labels = [], [], []
        for feature in features:
            missing = max_len - len(feature["input_ids"])
            batch_input.append(
                feature["input_ids"] + [tokenizer.pad_token_id] * missing
            )
            batch_mask.append(feature["attention_mask"] + [0] * missing)
            batch_labels.append(feature["labels"] + [-100] * missing)
        return {
            "input_ids": torch.tensor(batch_input, dtype=torch.long),
            "attention_mask": torch.tensor(batch_mask, dtype=torch.long),
            "labels": torch.tensor(batch_labels, dtype=torch.long),
        }

    training_args = TrainingArguments(
        output_dir=str(output_dir / "checkpoints"),
        max_steps=args.max_steps,
        per_device_train_batch_size=4,
        gradient_accumulation_steps=4,
        learning_rate=5e-5,
        warmup_steps=min(50, max(5, args.max_steps // 10)),
        lr_scheduler_type="cosine",
        logging_steps=10,
        save_steps=100,
        save_total_limit=2,
        fp16=torch.cuda.is_available(),
        bf16=False,
        gradient_checkpointing=True,
        dataloader_num_workers=0,
        report_to="none",
        optim="adamw_torch",
        seed=args.seed,
    )
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        data_collator=collate,
        processing_class=tokenizer,
    )
    result = trainer.train()
    final_dir = output_dir / "final"
    trainer.save_model(str(final_dir))
    tokenizer.save_pretrained(final_dir)

    metadata = {
        "model_type": "ANON instruction-tuned checkpoint",
        "base_model": str(base_dir),
        "dataset_file": data_path.name,
        "conversations_used": len(train_dataset),
        "max_length": args.max_length,
        "max_steps": args.max_steps,
        "train_loss": result.metrics.get("train_loss"),
        "assistant_only_loss": True,
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "sft_metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    LOG.info("Instruction-tuned model saved to %s", final_dir)


if __name__ == "__main__":
    main()
