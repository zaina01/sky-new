"""Generate Python stubs from the shared contract (build time only)."""
from pathlib import Path
from grpc_tools import protoc

root = Path(__file__).resolve().parent
source = root.parent / "sky-plugin-protocol/src/main/proto"
target = root / "generated"
target.mkdir(exist_ok=True)
raise SystemExit(protoc.main(["grpc_tools.protoc", f"-I{source}", f"--python_out={target}",
                              f"--grpc_python_out={target}", str(source / "sky_plugin.proto")]))
