#!/usr/bin/env python3
"""Continue pretraining an ANON model that was trained from random initialization.

This loads only the user's own local ANON checkpoint. It never downloads
pretrained model weights from an external model repository.
"""
from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path

import torch
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    DataCollatorForLanguageModeling,
    Trainer,
    TrainingArguments,
    set_seed,
)

from train_from_scratch import build_training_dataset, collect_corpus

LOG = logging.getLogger("anon_continue")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-model", required=True, help="Local ANON checkpoint folder")
    parser.add_argument("--dataset-id", default="Ethosoft/Turkish_corpus")
    parser.add_argument("--split", default="train")
    parser.add_argument("--text-column", default="text")
    parser.add_argument("--max-documents", type=int, default=50000)
    parser.add_argument("--max-document-chars", type=int, default=30000)
    parser.add_argument("--max-steps", type=int, default=1500)
    parser.add_argument("--block-size", type=int, default=512)
    parser.add_argument("--output-dir", default="./anon-ai-ultra-base-continued")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--resume-from-checkpoint", default=None)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.max_documents < 100:
        raise ValueError("--max-documents must be at least 100")
    if args.max_steps < 1:
        raise ValueError("--max-steps must be at least 1")

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
    )
    set_seed(args.seed)

    base_dir = Path(args.base_model).resolve()
    if not base_dir.is_dir():
        raise FileNotFoundError(f"Base model folder not found: {base_dir}")

    tokenizer = AutoTokenizer.from_pretrained(str(base_dir))
    model = AutoModelForCausalLM.from_pretrained(str(base_dir))
    block_size = min(args.block_size, int(model.config.n_positions))
    if block_size < 64:
        raise ValueError("The loaded model supports fewer than 64 positions.")

    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    data_dir = output_dir / "training_data"
    data_dir.mkdir(parents=True, exist_ok=True)
    text_path = data_dir / "train.txt"

    corpus_args = argparse.Namespace(
        dataset_id=args.dataset_id,
        split=args.split,
        text_column=args.text_column,
        max_documents=args.max_documents,
        max_document_chars=args.max_document_chars,
    )
    document_count = collect_corpus(corpus_args, text_path)
    train_dataset = build_training_dataset(text_path, tokenizer, block_size)

    model.config.use_cache = False
    if torch.cuda.is_available():
        LOG.info("GPU: %s", torch.cuda.get_device_name(0))
    else:
        LOG.warning("No GPU detected; CPU training may be very slow.")

    training_args = TrainingArguments(
        output_dir=str(output_dir / "checkpoints"),
        max_steps=args.max_steps,
        per_device_train_batch_size=2,
        gradient_accumulation_steps=8,
        learning_rate=1.5e-4,
        warmup_steps=min(100, max(10, args.max_steps // 10)),
        weight_decay=0.1,
        lr_scheduler_type="cosine",
        logging_steps=25,
        save_steps=200,
        save_total_limit=3,
        fp16=torch.cuda.is_available(),
        bf16=False,
        gradient_checkpointing=True,
        dataloader_num_workers=2,
        report_to="none",
        optim="adamw_torch",
        max_grad_norm=1.0,
        seed=args.seed,
    )
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        data_collator=DataCollatorForLanguageModeling(tokenizer=tokenizer, mlm=False),
        processing_class=tokenizer,
    )
    result = trainer.train(resume_from_checkpoint=args.resume_from_checkpoint)

    final_dir = output_dir / "final"
    trainer.save_model(str(final_dir))
    tokenizer.save_pretrained(final_dir)
    metadata = {
        "model_name": "ANON-TR-Base-30M-Continued",
        "initialization": "continued from the user's own random-init ANON checkpoint",
        "base_model": str(base_dir),
        "dataset_id": args.dataset_id,
        "dataset_split": args.split,
        "text_column": args.text_column,
        "documents_used": document_count,
        "training_blocks": len(train_dataset),
        "max_steps": args.max_steps,
        "block_size": block_size,
        "train_loss": result.metrics.get("train_loss"),
        "external_pretrained_weights_loaded": False,
        "warning": "Experimental base model; instruction tuning and evaluation are still required.",
    }
    (output_dir / "training_metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    LOG.info("Continued base model saved to %s", final_dir)


if __name__ == "__main__":
    main()
