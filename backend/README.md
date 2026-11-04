# SubsurfaceExplorer API

Small FastAPI service for project health, declared capabilities, and strict GeoJSON survey-region validation. This initial service does not retrieve satellite imagery or infer buried targets.

## Local run

```bash
python -m venv .venv
# Activate the environment, then:
python -m pip install -e ".[dev]"
uvicorn subsurface_api.main:app --reload
```

## Tests

```bash
pytest --cov=subsurface_api --cov-report=term-missing
ruff check .
```
