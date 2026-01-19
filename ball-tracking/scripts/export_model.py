#!/usr/bin/env python3
"""Export YOLO models to TensorFlow.js format for browser inference."""

import argparse
import shutil
from pathlib import Path
from ultralytics import YOLO


def export_model(model_name: str, output_dir: Path):
    """Export a YOLO model to TensorFlow.js format."""
    print(f"Loading {model_name}...")
    model = YOLO(f"{model_name}.pt")

    print("Exporting to TensorFlow.js format...")
    model.export(format="tfjs")

    # Move exported files to output directory
    export_dir = Path(f"{model_name}_web_model")
    if output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(export_dir, output_dir)

    # Calculate and print model size
    total_size = sum(f.stat().st_size for f in output_dir.rglob("*") if f.is_file())
    print(f"Model exported to {output_dir}")
    print(f"Total size: {total_size / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Export YOLO models to TensorFlow.js format"
    )
    parser.add_argument(
        "--model",
        default="yolo26s",
        help="Model name (yolo26n, yolo26s, yolo11n, yolov8n, etc.)",
    )
    parser.add_argument(
        "--output",
        default="../resources/public/model",
        help="Output directory (relative to scripts/)",
    )
    args = parser.parse_args()

    export_model(args.model, Path(args.output))
