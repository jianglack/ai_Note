# Third-Party Notices

AiNote source code is licensed under the Apache License 2.0 unless a file or
directory states otherwise. Third-party datasets and dependencies retain their
respective licenses.

## WildChat Replay Fixture

The file
`backend/src/test/resources/memory/external-real-human-replay-dataset.json`
contains 2,000 filtered user-message excerpts derived from the
[`allenai/WildChat`](https://huggingface.co/datasets/allenai/WildChat) dataset.

- Upstream dataset: `allenai/WildChat`
- Upstream license: Open Data Commons Attribution License 1.0 (`ODC-By-1.0`)
- License URI: https://opendatacommons.org/licenses/by/1-0/
- Upstream row provenance: retained per fixture case in `sourceReference`
- Changes made by AiNote: project-relevance filtering, PII/secret rejection,
  category labeling, deterministic policy labels, and omission of assistant
  responses
- The transformed fixture is test data, not AiNote product-user data

Contains information from WildChat, which is made available under the Open
Data Commons Attribution License.

The Apache License 2.0 for AiNote does not replace or restrict the ODC-By-1.0
terms that apply to the WildChat-derived fixture.
