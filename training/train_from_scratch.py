#!/usr/bin/env python3
"""Train a small Turkish GPT-style language model from random initialization.

This is an experimental pipeline, not a production assistant. It deliberately
does not call from_pretrained() or load any existing model checkpoint.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import random
import re
from pathlib import Path
from typing import Any

import torch
from datasets import load_dataset
from tokenizers import Tokenizer, decoders, models, normalizers, pre_tokenizers, trainers
from transformers import (
    DataCollatorForLanguageModeling,
    GPT2Config,
    GPT2LMHeadModel,
    PreTrainedTokenizerFast,
    Trainer,
    TrainingArguments,
    set_seed,
)

LOG = logging.getLogger("anon_training")
SPECIAL_TOKENS = ["<pad>", "<unk>", "<bos>", "<eos>"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-id", default="Ethosoft/Turkish_corpus")
    parser.add_argument("--split", default="train")
    parser.add_argument("--text-column", default="text")
    parser.add_argument("--max-documents", type=int, default=30000)
    parser.add_argument("--max-document-chars", type=int, default=30000)
    parser.add_argument("--vocab-size", type=int, default=16000)
    parser.add_argument("--block-size", type=int, default=512)
    parser.add_argument("--max-steps", type=int, default=500)
    parser.add_argument("--output-dir", default="./anon-ai-ultra-base-30m")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--resume-from-checkpoint", default=None)
    return parser.parse_args()


def clean_text(value: Any, max_chars: int) -> str:
    if not isinstance(value, str):
        return ""
    text = value.replace("\x00", " ")
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) < 80:
        return ""
    return text[:max_chars]


def collect_corpus(args: argparse.Namespace, text_path: Path) -> int:
    """Stream a bounded, deduplicated text sample to disk."""
    LOG.info("Loading dataset stream: %s (%s)", args.dataset_id, args.split)
    stream = load_dataset(args.dataset_id, split=args.split, streaming=True)
    seen: set[str] = set()
    written = 0

    text_path.parent.mkdir(parents=True, exist_ok=True)
    with text_path.open("w", encoding="utf-8") as output:
        for row in stream:
            language = str(row.get("language", "")).lower()
            if language and language not in {"tr", "turkish", "tur"}:
                continue

            text = clean_text(row.get(args.text_column), args.max_document_chars)
            if not text:
                continue

            digest = hashlib.sha1(text.encode("utf-8")).hexdigest()
            if digest in seen:
                continue
            seen.add(digest)

            output.write(text + "\n")
            written += 1
            if written % 1000 == 0:
                LOG.info("Collected %s documents", written)
            if written >= args.max_documents:
                break

    if written < 100:
        raise RuntimeError(
            f"Only {written} usable documents found. Check --text-column "
            f"('{args.text_column}') and the dataset schema."
        )
    LOG.info("Corpus ready: %s documents at %s", written, text_path)
    return written


def train_tokenizer(
    text_path: Path, vocab_size: int, save_dir: Path
) -> PreTrainedTokenizerFast:
    LOG.info("Training a new ByteLevel BPE tokenizer")
    tokenizer = Tokenizer(models.BPE(unk_token="<unk>"))
    tokenizer.normalizer = normalizers.NFC()
    tokenizer.pre_tokenizer = pre_tokenizers.ByteLevel(add_prefix_space=False)
    tokenizer.decoder = decoders.ByteLevel()

    tokenizer_trainer = trainers.BpeTrainer(
        vocab_size=vocab_size,
        min_frequency=2,
        special_tokens=SPECIAL_TOKENS,
        initial_alphabet=pre_tokenizers.ByteLevel.alphabet(),
        show_progress=True,
    )
    tokenizer.train(files=[str(text_path)], trainer=tokenizer_trainer)

    fast_tokenizer = PreTrainedTokenizerFast(
        tokenizer_object=tokenizer,
        unk_token="<unk>",
        pad_token="<pad>",
        bos_token="<bos>",
        eos_token="<eos>",
        model_max_length=512,
    )
    fast_tokenizer.save_pretrained(save_dir)
    LOG.info("Tokenizer trained: %s tokens", len(fast_tokenizer))
    return fast_tokenizer


def build_training_dataset(
    text_path: Path, tokenizer: PreTrainedTokenizerFast, block_size: int
):
    """Tokenize documents and pack them into fixed-length causal-LM blocks."""
    raw = load_dataset("text", data_files=str(text_path), split="train")
    eos_id = tokenizer.eos_token_id
    bos_id = tokenizer.bos_token_id

    def tokenize_batch(batch: dict[str, list[str]]) -> dict[str, list[list[int]]]:
        encoded = tokenizer(
            batch["text"], add_special_tokens=False, truncation=False
        )
        packed_docs = []
        for ids in encoded["input_ids"]:
            if ids:
                packed_docs.append([bos_id] + ids + [eos_id])
        return {"input_ids": packed_docs}

    tokenized = raw.map(
        tokenize_batch,
        batched=True,
        batch_size=128,
        remove_columns=raw.column_names,
        desc="Tokenizing corpus",
    )

    def group_texts(batch: dict[str, list[list[int]]]) -> dict[str, list[list[int]]]:
        combined: list[int] = []
        for sequence in batch["input_ids"]:
            combined.extend(sequence)
        usable = (len(combined) // block_size) * block_size
        if usable == 0:
            return {"input_ids": [], "attention_mask": []}
        input_ids = [
            combined[index : index + block_size]
            for index in range(0, usable, block_size)
        ]
        return {
            "input_ids": input_ids,
            "attention_mask": [[1] * block_size for _ in input_ids],
        }

    grouped = tokenized.map(
        group_texts,
        batched=True,
        batch_size=1000,
        desc="Packing token blocks",
    )
    grouped = grouped.filter(lambda row: len(row["input_ids"]) == block_size)
    if len(grouped) == 0:
        raise RuntimeError(
            "No training blocks were created. Increase the corpus sample."
        )
    LOG.info("Prepared %s training blocks", len(grouped))
    return grouped


def main() -> None:
    args = parse_args()
    if args.max_documents < 100 or args.max_steps < 1:
        raise ValueError(
            "--max-documents must be >= 100 and --max-steps must be >= 1"
        )
    if args.block_size < 64 or args.block_size > 1024:
        raise ValueError("--block-size must be between 64 and 1024")

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
    )
    set_seed(args.seed)
    random.seed(args.seed)
    torch.manual_seed(args.seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(args.seed)

    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    data_dir = output_dir / "training_data"
    data_dir.mkdir(parents=True, exist_ok=True)
    text_path = data_dir / "train.txt"

    document_count = collect_corpus(args, text_path)
    tokenizer = train_tokenizer(text_path, args.vocab_size, output_dir / "tokenizer")
    train_dataset = build_training_dataset(text_path, tokenizer, args.block_size)

    config = GPT2Config(
        vocab_size=len(tokenizer),
        n_positions=args.block_size,
        n_ctx=args.block_size,
        n_embd=512,
        n_layer=8,
        n_head=8,
        n_inner=2048,
        activation_function="gelu_new",
        resid_pdrop=0.1,
        embd_pdrop=0.1,
        attn_pdrop=0.1,
        bos_token_id=tokenizer.bos_token_id,
        eos_token_id=tokenizer.eos_token_id,
        pad_token_id=tokenizer.pad_token_id,
        use_cache=False,
    )

    # Critical: initialize a new model from config (random weights).
    # Do not replace this with GPT2LMHeadModel.from_pretrained().
    model = GPT2LMHeadModel(config)
    parameter_count = sum(
        parameter.numel() for parameter in model.parameters()
    )
    LOG.info(
        "Initialized new model from random weights: %.2fM parameters",
        parameter_count / 1_000_000,
    )

    if torch.cuda.is_available():
        LOG.info("GPU: %s", torch.cuda.get_device_name(0))
    else:
        LOG.warning("No GPU detected. CPU training will be very slow.")

    training_args = TrainingArguments(
        output_dir=str(output_dir / "checkpoints"),
        max_steps=args.max_steps,
        per_device_train_batch_size=2,
        gradient_accumulation_steps=8,
        learning_rate=3e-4,
        warmup_steps=min(100, max(10, args.max_steps // 10)),
        weight_decay=0.1,
        lr_scheduler_type="cosine",
        logging_steps=25,
        save_steps=200,
        save_total_limit=2,
        fp16=torch.cuda.is_available(),
        bf16=False,
        gradient_checkpointing=True,
        dataloader_num_workers=2,
        report_to="none",
        optim="adamw_torch",
        max_grad_norm=1.0,
        seed=args.seed,
    )

    collator = DataCollatorForLanguageModeling(tokenizer=tokenizer, mlm=False)
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        data_collator=collator,
        processing_class=tokenizer,
    )

    train_result = trainer.train(
        resume_from_checkpoint=args.resume_from_checkpoint
    )
    final_dir = output_dir / "final"
    trainer.save_model(str(final_dir))
    tokenizer.save_pretrained(final_dir)

    metadata = {
        "model_name": "ANON-TR-Base-30M-Experimental",
        "initialization": "random",
        "architecture": "GPT-2 decoder",
        "parameters": parameter_count,
        "layers": 8,
        "attention_heads": 8,
        "hidden_size": 512,
        "block_size": args.block_size,
        "vocab_size": len(tokenizer),
        "dataset_id": args.dataset_id,
        "dataset_split": args.split,
        "text_column": args.text_column,
        "documents_used": document_count,
        "max_steps": args.max_steps,
        "train_loss": train_result.metrics.get("train_loss"),
        "warning": (
            "Experimental base model only; instruction tuning and evaluation "
            "are still required."
        ),
    }
    with (output_dir / "training_metadata.json").open(
        "w", encoding="utf-8"
    ) as handle:
        json.dump(metadata, handle, ensure_ascii=False, indent=2)

    LOG.info("Training complete. Model files: %s", final_dir)
    LOG.info("This base model is not yet a chat assistant.")


if __name__ == "__main__":
    main()
