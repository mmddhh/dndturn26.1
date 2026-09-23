"""Generate the small stone platform used by local_time GameTest."""

from pathlib import Path
import gzip
import struct


def named(tag_type: int, name: str, payload: bytes) -> bytes:
    encoded = name.encode("utf-8")
    return bytes([tag_type]) + struct.pack(">H", len(encoded)) + encoded + payload


def string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def int_list(values: tuple[int, ...]) -> bytes:
    return bytes([3]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", value) for value in values)


def compound(entries: bytes) -> bytes:
    return entries + b"\0"


palette = compound(named(8, "Name", string("minecraft:stone")))
blocks = []
for x in range(5):
    for z in range(5):
        blocks.append(compound(named(9, "pos", int_list((x, 0, z))) + named(3, "state", struct.pack(">i", 0))))

root = compound(
    named(9, "size", int_list((5, 3, 5)))
    + named(9, "palette", bytes([10]) + struct.pack(">i", 1) + palette)
    + named(9, "blocks", bytes([10]) + struct.pack(">i", len(blocks)) + b"".join(blocks))
    + named(9, "entities", bytes([10]) + struct.pack(">i", 0))
)
output = Path(__file__).resolve().parent.parent / "src/main/resources/data/dndturn/structure/local_time_arena.nbt"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_bytes(gzip.compress(b"\x0a\x00\x00" + root, mtime=0))
print(output)
