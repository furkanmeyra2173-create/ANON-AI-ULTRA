# ANON AI ULTRA — model training

This folder is an isolated experimental training pipeline. It does **not** change the Android app or its current Gemini connection.

## Goal

Build an ANON-owned Turkish causal language model with randomly initialized weights. The first checkpoint is deliberately small (about 30–35M parameters) and is a training-pipeline proof of concept, **not** a capable ChatGPT replacement.

## Training stages

1. **Tokenizer from scratch** — Byte-level BPE, 16,000-token vocabulary, trained only on the selected Turkish corpus.
2. **Base model from scratch** — GPT-2-style decoder, 8 layers, 8 attention heads, 512 hidden size, 512-token training sequences. No pretrained model weights are loaded.
3. **Instruction tuning** — a later stage will teach the base model to respond to user instructions and conversations using a separately curated, license-reviewed Turkish dataset.
4. **Evaluation and Android runtime** — test Turkish quality, factuality, safety, speed and memory use before replacing any provider in the app.

## Why the first model is small

Large language models require enormous amounts of text and GPU time. A public Turkish 473M-parameter scratch model reports about 14.9B training tokens. A free notebook GPU is useful for proving the pipeline, not reproducing that scale. The initial 30–35M model will likely be weak and may produce repetitive or nonsensical answers. Do not publish it as a finished assistant.

## Run on a Kaggle notebook

The Android phone is only used to manage the repository and notebook. Training runs on the notebook's assigned GPU.

1. Sign in to Kaggle and create/import a notebook from the file training/ANON_AI_ULTRA_Kaggle.ipynb.
2. In notebook settings, select **GPU** and enable **Internet**.
3. Run the cells from top to bottom. The script downloads a small streaming sample, trains a tokenizer and initializes a new model from random weights.
4. The model output is written under /kaggle/working/anon-ai-ultra-base-30m.
5. Use Kaggle's notebook Output panel to save/download the resulting model artifact. Keep checkpoints; free GPU sessions can end.

Kaggle free GPU access is limited and availability can vary. A run can queue or stop; resume support is a future improvement.

## Dataset and licensing

Default experiment dataset: Ethosoft/Turkish_corpus (dataset card currently declares MIT). The dataset card also identifies upstream source collections. A dataset's top-level license does not automatically settle every upstream content right. Before commercial use, inspect upstream licenses, attribution requirements, privacy issues and any source-specific restrictions. Do not train on private conversations or personal data without a lawful basis and explicit authorization.

To use a different dataset, pass --dataset-id, --split and --text-column. The script expects a public Hugging Face dataset with a text column. Use only material you are entitled to use.

## Local command

~~~bash
pip install -r training/requirements.txt
python training/train_from_scratch.py \
  --dataset-id Ethosoft/Turkish_corpus \
  --split train \
  --text-column text \
  --max-documents 30000 \
  --max-steps 500 \
  --output-dir ./anon-ai-ultra-base-30m
~~~

For a small smoke run, reduce --max-documents and --max-steps. That confirms the code path only; it does not produce a useful conversational assistant.

## Fallback path (choice 2)

If scratch training cannot reach usable quality within the available compute, evaluate a permissively licensed open-weight Turkish or multilingual base model and fine-tune it with our own instruction dataset. Keep the training data and model license records with each release. This fallback is not silently enabled by the current script.

## Important

- The app continues using its current provider until an ANON model has passed evaluation and Android integration tests.
- Do not put API keys, access tokens, passwords or private datasets in this repository.
- A small model trained from scratch is not automatically knowledgeable, safe, aligned or good at coding.


## Instruction tuning

After the from-scratch base model exists, prepare your own or appropriately licensed conversation data as JSON Lines. Each line must be one JSON object containing a messages array, for example:

~~~json
{"messages":[{"role":"user","content":"Merhaba"},{"role":"assistant","content":"Merhaba! Sana nasıl yardımcı olabilirim?"}]}
~~~

Use at least 10 valid conversations for a technical smoke run; meaningful quality requires substantially more, carefully reviewed examples. The script computes loss only on assistant message content, not on system/user text.

~~~bash
python training/finetune_instructions.py \
  --base-model ./anon-ai-ultra-base-30m/final \
  --dataset-jsonl ./my_turkish_instructions.jsonl \
  --max-steps 500 \
  --output-dir ./anon-ai-ultra-chat
~~~

Then test the result:

~~~bash
python training/test_chat_model.py --model ./anon-ai-ultra-chat/final
~~~

Do not use private chat histories or scraped personal data without permission. Keep source, license and attribution information alongside each dataset version.
