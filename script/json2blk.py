#!/usr/bin/env python3
"""
Convert War Thunder JSON-format flight model data to Dagor .blk format.
Usage: python json2blk.py <input.json> [output.blkx]
"""

import json
import sys
import os


def detect_type(val):
    """Detect Dagor type annotation for a value."""
    if isinstance(val, bool):
        return "b"
    if isinstance(val, (int, float)):
        return "r"
    if isinstance(val, str):
        return "t"
    return None


def format_value(val):
    """Format a value for Dagor .blk output."""
    if isinstance(val, bool):
        return "true" if val else "false"
    if isinstance(val, (int, float)):
        return str(val)
    if isinstance(val, str):
        return f'"{val}"'
    return str(val)


def format_array(arr):
    """Format an array for Dagor .blk output."""
    if not arr:
        return ""
    if isinstance(arr[0], (int, float)):
        return ", ".join(str(v) for v in arr)
    if isinstance(arr[0], str):
        return ", ".join(f'"{v}"' for v in arr)
    return ", ".join(str(v) for v in arr)


def write_blk(obj, out, indent=0, array_key=None, array_index=None):
    """
    Recursively write a JSON object as Dagor .blk format.
    """
    prefix = "  " * indent

    if isinstance(obj, dict):
        for key, val in obj.items():
            dagor_key = key

            if isinstance(val, dict):
                # Nested block
                out.write(f"{prefix}{dagor_key}\n")
                out.write(f"{prefix}{{\n")
                write_blk(val, out, indent + 1)
                out.write(f"{prefix}}}\n")
            elif isinstance(val, list):
                if len(val) == 0:
                    continue
                # Check if list contains objects
                if isinstance(val[0], dict):
                    # Array of objects: use index as part of key name
                    for i, item in enumerate(val):
                        indexed_key = f"{dagor_key}{i}"
                        out.write(f"{prefix}{indexed_key}\n")
                        out.write(f"{prefix}{{\n")
                        write_blk(item, out, indent + 1)
                        out.write(f"{prefix}}}\n")
                else:
                    # Array of primitives
                    type_tag = detect_type(val[0])
                    arr_len = len(val)
                    if arr_len <= 4:
                        arr_tag = f"p{arr_len}"
                    else:
                        arr_tag = f"p{arr_len}"
                    out.write(f"{prefix}{dagor_key}:{arr_tag} = {format_array(val)}\n")
            else:
                type_tag = detect_type(val)
                if type_tag is None:
                    continue
                out.write(f"{prefix}{dagor_key}:{type_tag} = {format_value(val)}\n")

    elif isinstance(obj, list):
        for i, item in enumerate(obj):
            if isinstance(item, dict):
                key = array_key or "item"
                indexed_key = f"{key}{i}" if array_index is None else f"{array_key}{i}"
                out.write(f"{prefix}{indexed_key}\n")
                out.write(f"{prefix}{{\n")
                write_blk(item, out, indent + 1)
                out.write(f"{prefix}}}\n")


def main():
    if len(sys.argv) < 2:
        print("Usage: python json2blk.py <input.json> [output.blkx]")
        sys.exit(1)

    input_path = sys.argv[1]
    output_path = sys.argv[2] if len(sys.argv) > 2 else input_path.rsplit(".", 1)[0] + ".blkx"

    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    with open(output_path, "w", encoding="utf-8", newline="\n") as f:
        if isinstance(data, dict):
            # Top-level: flat dict, each key-value becomes a line or block
            write_blk_entries(data, f, indent=0)
        else:
            write_blk(data, f, indent=0)

    print(f"Converted: {input_path} -> {output_path}")
    print(f"Output size: {os.path.getsize(output_path)} bytes")


def write_blk_entries(obj, out, indent=0):
    """Write top-level or nested dict entries."""
    prefix = "  " * indent
    for key, val in obj.items():
        if isinstance(val, dict):
            out.write(f"{prefix}{key}\n")
            out.write(f"{prefix}{{\n")
            write_blk_entries(val, out, indent + 1)
            out.write(f"{prefix}}}\n")
        elif isinstance(val, list):
            if len(val) == 0:
                continue
            if isinstance(val[0], dict):
                # Array of objects
                out.write(f"{prefix}{key}\n")
                out.write(f"{prefix}{{\n")
                for i, item in enumerate(val):
                    indexed_key = f"{key}{i}"
                    out.write(f"{prefix}  {indexed_key}\n")
                    out.write(f"{prefix}  {{\n")
                    write_blk_entries(item, out, indent + 2)
                    out.write(f"{prefix}  }}\n")
                out.write(f"{prefix}}}\n")
            else:
                # Array of primitives
                type_tag = detect_type(val[0])
                out.write(f"{prefix}{key}:p{len(val)} = {format_array(val)}\n")
        else:
            type_tag = detect_type(val)
            if type_tag is None:
                continue
            out.write(f"{prefix}{key}:{type_tag} = {format_value(val)}\n")


if __name__ == "__main__":
    main()
