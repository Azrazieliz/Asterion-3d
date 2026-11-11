#!/usr/bin/env python3
"""Independent binary-STL geometry/unit validation; not an actual slicer."""
import collections
import math
from pathlib import Path
import struct
import sys

TRI = struct.Struct("<12fH")
HEADER = struct.Struct("<I")


def validate(path: Path):
    size = path.stat().st_size
    with path.open("rb") as source:
        title = source.read(80)
        if b"ASTERION FOUNDRY STL" not in title or b"MM" not in title:
            raise AssertionError("Unsupported print geometry provenance or units")
        (count,) = HEADER.unpack(source.read(4))
        if count < 1 or 84 + count * TRI.size != size:
            raise AssertionError("Invalid STL length or triangle count")
        bounds = [[float("inf")] * 3, [float("-inf")] * 3]
        edges = collections.Counter()
        oriented = collections.Counter()
        signed_volume = 0.0
        for i in range(count):
            data = source.read(TRI.size)
            if len(data) != TRI.size:
                raise AssertionError("STL ended before the advertised triangle count")
            *values, attr = TRI.unpack(data)
            if attr != 0 or not all(math.isfinite(v) for v in values):
                raise AssertionError("Invalid STL vertex or attribute")
            n = values[:3]
            if abs(math.sqrt(sum(x*x for x in n)) - 1.0) > 1e-4:
                raise AssertionError("Normal not normalized")
            p = [values[3+j*3:6+j*3] for j in range(3)]
            a, b, c = p
            ux, uy, uz = (b[k]-a[k] for k in range(3))
            vx, vy, vz = (c[k]-a[k] for k in range(3))
            cross = (uy*vz-uz*vy, uz*vx-ux*vz, ux*vy-uy*vx)
            if sum(x*x for x in cross) < 1e-10:
                raise AssertionError("Degenerate STL triangle")
            if sum(cross[k]*n[k] for k in range(3)) <= 0:
                raise AssertionError("STL normal points away from triangle winding")
            signed_volume += sum(a[k] * cross[k] for k in range(3)) / 6.0
            for v in p:
                for axis in range(3):
                    bounds[0][axis] = min(bounds[0][axis], v[axis])
                    bounds[1][axis] = max(bounds[1][axis], v[axis])
            q = [tuple(round(value * 100) for value in vertex) for vertex in p]
            for j in range(3):
                edge = (q[j], q[(j + 1) % 3])
                key = tuple(sorted(edge))
                edges[key] += 1
                oriented[key] += 1 if edge[0] == key[0] else -1
        if source.read(1):
            raise AssertionError("Trailing STL bytes")
    boundary = sum(value == 1 for value in edges.values())
    nonmanifold = sum(value > 2 for value in edges.values())
    orientation_conflicts = sum(value != 0 for value in oriented.values())
    if boundary or nonmanifold or orientation_conflicts or signed_volume <= 1e-3:
        raise AssertionError(f"Invalid STL topology: open={boundary} nonmanifold={nonmanifold}"
                             f" winding={orientation_conflicts} volume={signed_volume}")
    if bounds[0][2] < -0.001:
        raise AssertionError("STL is not anchored to the Z=0 print bed")
    size_mm = tuple(bounds[1][k]-bounds[0][k] for k in range(3))
    if not all(0 < v < 10000 for v in size_mm):
        raise AssertionError("Unusable print envelope")
    print(f"PASS independent STL {path}: {count} triangles, mm bounds {size_mm}, "
          f"volume {signed_volume:.1f} mm³, closed edges {len(edges)}")
    return size_mm

if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("Usage: validate_stl.py path [path...]")
    for file in sys.argv[1:]:
        dims = validate(Path(file))
        if Path(file).name == "print_box.stl" and abs(dims[2]-180) > .02:
            raise AssertionError("Print unit scale failed independent 180 mm height gate")
        if Path(file).name == "print_large.stl" and abs(dims[2]-140) > .02:
            raise AssertionError("Large print unit scale failed independent 140 mm height gate")
