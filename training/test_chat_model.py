#!/usr/bin/env python3
"""Interactive smoke-test for an ANON instruction-tuned checkpoint."""

from __future__ import annotations

import argparse
from pathlib import Path

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, help="Folder containing the final model")
    parser.add_argument("--max-new-tokens", type=int, default=160)
    args = parser.parse_args()

    model_path = Path(args.model)
    if not model_path.is_dir():
        raise FileNotFoundError(f"Model folder does not exist: {model_path}")

    tokenizer = AutoTokenizer.from_pretrained(str(model_path))
    model = AutoModelForCausalLM.from_pretrained(str(model_path))
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model.to(device)
    model.eval()

    print("ANON model test. Exit with /q.")
    while True:
        try:
            question = input("\nSen: ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if question.lower() in {"/q", "/quit", "exit"}:
            break
        if not question:
            continue

        prompt = (
            "<|user|>\n" + question + "\n" + tokenizer.eos_token
            + "\n<|assistant|>\n"
        )
        inputs = tokenizer(prompt, return_tensors="pt").to(device)
        with torch.inference_mode():
            output = model.generate(
                **inputs,
                max_new_tokens=args.max_new_tokens,
                do_sample=True,
                temperature=0.8,
                top_p=0.9,
                repetition_penalty=1.1,
                eos_token_id=tokenizer.eos_token_id,
                pad_token_id=tokenizer.pad_token_id,
            )
        generated = output[0][inputs["input_ids"].shape[1]:]
        print("ANON:", tokenizer.decode(generated, skip_special_tokens=True).strip())


if __name__ == "__main__":
    main()
