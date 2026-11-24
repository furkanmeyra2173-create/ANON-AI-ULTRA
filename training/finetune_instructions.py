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
    parser.add_argument("--max-samples", type=int, default=20000)
    parser.add_argument("--max-length", type=int, default=512)
    parser.add_argument("--max-steps", type=int, default=800)
    parser.add_argument("--eval-ratio", type=float, default=0.05)
    parser.add_argument("--eval-steps", type=int, default=50)
    parser.add_argument("--batch-size", type=int, default=2)
    parser.add_argument("--gradient-accumulation-steps", type=int, default=8)
    parser.add_argument("--learning-rate", type=float, default=2e-5)
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

    if len(records) < 20:
        raise RuntimeError(
            f"Only {len(records)} valid conversations found. "
            "Provide at least 20 JSONL rows for a train/evaluation split."
        )
    return records


def prepare_example(
    messages: list[dict[str, Any]],
    tokenizer,
    max_length: int,
) -> dict[str, list[int]] | None:
    """Encode messages and retain the newest complete message window that fits."""
    chunks: list[tuple[str, list[int], list[int]]] = []
    eos_id = tokenizer.eos_token_id
    for item in messages:
        if not isinstance(item, dict):
            continue
        role = item.get("role")
        content = item.get("content")
        if role not in ROLE_TOKENS or not isinstance(content, str) or not content.strip():
            continue

        marker_id = tokenizer.convert_tokens_to_ids(ROLE_TOKENS[role])
        if marker_id is None or marker_id == tokenizer.unk_token_id:
            continue
        content_ids = tokenizer.encode(content.strip(), add_special_tokens=False)
        message_ids = [marker_id] + content_ids + [eos_id]
        if role == "assistant":
            message_labels = [-100] + content_ids + [eos_id]
        else:
            message_labels = [-100] * len(message_ids)
        chunks.append((role, message_ids, message_labels))

    while chunks and sum(len(chunk[1]) for chunk in chunks) > max_length:
        chunks.pop(0)

    if not chunks:
        return None
    if not any(role == "user" for role, _, _ in chunks):
        return None

    input_ids = [token for _, ids, _ in chunks for token in ids]
    labels = [token for _, _, target in chunks for token in target]
    if not any(label != -100 for label in labels):
        return None

    return {
        "input_ids": input_ids,
        "attention_mask": [1] * len(input_ids),
        "labels": labels,
    }


def main() -> None:
    args = parse_args()
    if not 0.01 <= args.eval_ratio <= 0.3:
        raise ValueError("--eval-ratio must be between 0.01 and 0.3")
    if args.max_length < 64 or args.max_length > 2048:
        raise ValueError("--max-length must be between 64 and 2048")
    if args.max_steps < 1 or args.eval_steps < 1:
        raise ValueError("--max-steps and --eval-steps must be at least 1")
    if args.batch_size < 1 or args.gradient_accumulation_steps < 1:
        raise ValueError("Batch and gradient-accumulation sizes must be positive")

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
    )
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

    if len(prepared) < 20:
        raise RuntimeError(
            f"Only {len(prepared)} conversations fit max-length={args.max_length}. "
            "Increase --max-length or inspect the source data."
        )

    dataset = Dataset.from_list(prepared)
    split = dataset.train_test_split(test_size=args.eval_ratio, seed=args.seed)
    train_dataset = split["train"]
    eval_dataset = split["test"]
    LOG.info(
        "Prepared %s training and %s evaluation conversations",
        len(train_dataset),
        len(eval_dataset),
    )

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

    eval_steps = min(args.eval_steps, args.max_steps)
    training_args = TrainingArguments(
        output_dir=str(output_dir / "checkpoints"),
        max_steps=args.max_steps,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
        gradient_accumulation_steps=args.gradient_accumulation_steps,
        learning_rate=args.learning_rate,
        warmup_steps=min(50, args.max_steps // 10),
        lr_scheduler_type="cosine",
        logging_steps=10,
        eval_strategy="steps",
        eval_steps=eval_steps,
        save_strategy="steps",
        save_steps=eval_steps,
        save_total_limit=2,
        load_best_model_at_end=True,
        metric_for_best_model="eval_loss",
        greater_is_better=False,
        fp16=torch.cuda.is_available(),
        bf16=False,
        gradient_checkpointing=True,
        dataloader_num_workers=0,
        report_to="none",
        optim="adamw_torch",
        max_grad_norm=1.0,
        seed=args.seed,
    )
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        data_collator=collate,
        processing_class=tokenizer,
    )
    result = trainer.train()
    final_dir = output_dir / "final"
    trainer.save_model(str(final_dir))
    tokenizer.save_pretrained(final_dir)

    source_metadata_path = data_path.with_suffix(data_path.suffix + ".metadata.json")
    source_metadata = {}
    if source_metadata_path.is_file():
        try:
            source_metadata = json.loads(source_metadata_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            LOG.warning("Could not parse dataset metadata sidecar.")

    metadata = {
        "model_type": "ANON instruction-tuned checkpoint",
        "base_model": str(base_dir),
        "dataset_file": data_path.name,
        "dataset_metadata": source_metadata,
        "conversations_total": len(dataset),
        "conversations_train": len(train_dataset),
        "conversations_eval": len(eval_dataset),
        "max_length": args.max_length,
        "max_steps": args.max_steps,
        "eval_ratio": args.eval_ratio,
        "train_loss": result.metrics.get("train_loss"),
        "assistant_only_loss": True,
        "best_eval_loss": trainer.state.best_metric,
        "warning": "Experimental; evaluate quality and safety before deployment.",
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "sft_metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    LOG.info("Instruction-tuned model saved to %s", final_dir)


if __name__ == "__main__":
    main()
