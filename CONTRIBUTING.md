# Contributing

1. Create a focused branch from `main`.
2. Keep Android UI, domain geometry, and provider/data code separated.
3. Add tests for geometry, parsing, failure cases, and scientific assumptions.
4. Run backend tests and lint before opening a pull request. Build the Android debug variant when Android SDK tooling is available.
5. Document the provider, product ID, licence, resolution, processing baseline, and quality masks for every new Earth-observation integration.
6. Do not introduce target-class or depth claims without documented validation and ground truth.
7. Never add credentials or private survey data to commits.

Pull requests should describe behaviour, verification performed, limitations, and any data/licence implications.
