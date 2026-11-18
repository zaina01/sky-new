"""Package the demos without installing dependencies or importing their code."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parent.parent
output = root / "build/script-plugins"
output.mkdir(parents=True, exist_ok=True)
for language in ("node", "python"):
    source = root / f"plugin-demo-{language}"
    target = output / f"plugin-demo-{language}.zip"
    with ZipFile(target, "w", ZIP_DEFLATED) as archive:
        for path in source.rglob("*"):
            if path.is_file() and "__pycache__" not in path.parts:
                archive.write(path, path.relative_to(source).as_posix())
    print(target)
