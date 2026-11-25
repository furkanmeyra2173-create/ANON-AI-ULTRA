# ANON AI ULTRA — model training

This is an experimental training pipeline. It does **not** change the Android app or its current Gemini connection.

## Goal and current limits

The initial model is an ANON-owned Turkish causal language model created from random initialization:
- Byte-level BPE tokenizer with a 16,000-token vocabulary.
- GPT-2-style decoder with 8 layers, 8 attention heads and 512 hidden size.
- Approximately 30–35M parameters.

The first run (30,000 documents / 500 steps) proved that training, saving and text generation work. It did **not** create a useful assistant: the model may repeat tokens or generate nonsense. More pretraining, instruction tuning and evaluation are required. This size and compute budget are far below what is normally needed for a strong general assistant.

## Training stages

1. **Scratch training** — tokenizer and model weights are initialized from scratch.
2. **Continued pretraining** — `continue_pretraining.py` loads only the local ANON checkpoint and trains those same weights on more Turkish text. It does not download third-party model weights.
3. **Instruction data preparation** — `prepare_instruction_data.py` downloads a public Turkish conversation dataset, applies language/tool filters, normalizes messages, removes duplicates and writes JSONL plus a metadata sidecar.
4. **Supervised fine-tuning (SFT)** — `finetune_instructions.py` learns from assistant responses only, holds out an evaluation split and saves the best checkpoint by evaluation loss.
5. **Evaluation and Android runtime** — test Turkish quality, factuality, repetition, safety, speed and memory use before considering an Android integration.

## Google Colab from an Android phone

**Initial scratch-training notebook:**

https://colab.research.google.com/github/furkanmeyra2173-create/ANON-AI-ULTRA/blob/main/training/ANON_AI_ULTRA_Kaggle.ipynb

After the initial run, download `ANON-AI-ULTRA-base-30m.zip`.

**Next-stage notebook (continue pretraining + SFT):**

https://colab.research.google.com/github/furkanmeyra2173-create/ANON-AI-ULTRA/blob/main/training/ANON_AI_ULTRA_SFT_Colab.ipynb

**Checkpoint test notebook (no retraining):**

https://colab.research.google.com/github/furkanmeyra2173-create/ANON-AI-ULTRA/blob/main/training/ANON_AI_ULTRA_Test_Colab.ipynb

The test notebook checks tokenizer/model vocabulary sizes, the three chat-marker tokens and Turkish text round-tripping before generation. Inference uses the same message prefix as SFT: `<|user|>{question}<|eos|><|assistant|>` (where `<|eos|>` is the tokenizer's configured EOS token). Do not add literal backslash-n text or a different chat template.

1. Open the second notebook and select a GPU runtime.
2. Run the cells from top to bottom.
3. When asked, upload the `ANON-AI-ULTRA-base-30m.zip` downloaded from the first notebook.
4. The notebook continues training the local ANON model, prepares Turkish chat data, runs SFT and tests a sample response.
5. Download and keep `ANON-AI-ULTRA-chat.zip`.

The notebook currently uses up to 50,000 additional Turkish corpus documents / 1,200 continuation steps and up to 20,000 chat samples / 800 SFT steps. These are experimental settings, not a promise of assistant-level quality. Free GPU access and session duration are not guaranteed.

## Datasets and licensing

### Base-language training

The initial and continued pretraining default to `Ethosoft/Turkish_corpus`. Its dataset card declares MIT, but also identifies upstream source collections. A top-level dataset license does not automatically settle the rights of every upstream item. Inspect upstream licenses, attribution, privacy and source restrictions before redistributing weights or using the model commercially.

### Instruction tuning

The default source is `cisimcik/turkish-chat-max-25k`. Its Hugging Face dataset card declares Apache-2.0 and describes 24,543 synthetic Turkish instruction/conversation records. The notebook selects only `language_mode=tr_only`, `prompt_language=tr`, and records without tool interactions. Source and license information are written to the JSONL metadata sidecar. Preserve the source's license/notice information and credit the dataset in model documentation.

Dataset card: https://huggingface.co/datasets/cisimcik/turkish-chat-max-25k

Do not train on private conversations or personal data without a lawful basis and explicit authorization. Review the actual downloaded examples before release; dataset filters do not guarantee that every sample is accurate or suitable.

## Local commands

Install dependencies:

~~~bash
pip install -r training/requirements.txt
~~~

Initial scratch training:

~~~bash
python training/train_from_scratch.py \
  --dataset-id Ethosoft/Turkish_corpus \
  --split train \
  --text-column text \
  --max-documents 30000 \
  --max-steps 500 \
  --output-dir ./anon-ai-ultra-base-30m
~~~

Continue training the local ANON checkpoint:

~~~bash
python training/continue_pretraining.py \
  --base-model ./anon-ai-ultra-base-30m/final \
  --dataset-id Ethosoft/Turkish_corpus \
  --split train \
  --text-column text \
  --max-documents 50000 \
  --max-steps 1200 \
  --output-dir ./anon-ai-ultra-base-continued
~~~

Prepare the filtered instruction dataset:

~~~bash
python training/prepare_instruction_data.py \
  --dataset-id cisimcik/turkish-chat-max-25k \
  --config all \
  --split train \
  --language-mode tr_only \
  --prompt-language tr \
  --max-samples 20000 \
  --output ./turkish_chat_train.jsonl
~~~

Run SFT:

~~~bash
python training/finetune_instructions.py \
  --base-model ./anon-ai-ultra-base-continued/final \
  --dataset-jsonl ./turkish_chat_train.jsonl \
  --max-steps 800 \
  --eval-ratio 0.05 \
  --output-dir ./anon-ai-ultra-chat
~~~

Test an instruction-tuned checkpoint:

~~~bash
python training/test_chat_model.py --model ./anon-ai-ultra-chat/final
~~~

## Important safeguards

- The Android app continues using its current provider until an ANON model has passed quality, safety and Android performance tests.
- Do not put API keys, access tokens, passwords or private datasets in this repository.
- A small model trained from scratch is not automatically knowledgeable, safe, aligned or good at coding.
- The scripts are an experimental pipeline. Inspect training/evaluation losses and generated answers; a falling loss alone does not prove that responses are useful or factual.
