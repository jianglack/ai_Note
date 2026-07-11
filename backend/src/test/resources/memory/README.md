# Memory Evaluation Data

This directory contains governed test fixtures for memory capture, replay, and
answer-quality evaluation. It must not contain raw AiNote production data.

`external-real-human-replay-dataset.json` contains 2,000 filtered and redacted
user-message excerpts derived from `allenai/WildChat`. WildChat is available at
https://huggingface.co/datasets/allenai/WildChat under the Open Data Commons
Attribution License 1.0:
https://opendatacommons.org/licenses/by/1-0/.

Each imported case retains an upstream row and conversation reference. AiNote
removed assistant responses, rejected configured PII/secret patterns, selected
memory-governance scenarios, and added deterministic evaluation labels. These
records are external human conversation samples, not AiNote product-user data.

See the repository `THIRD_PARTY_NOTICES.md` for the complete attribution.
