#!/usr/bin/env python3
"""Start an installed backend with its private configuration."""

import json
import os
from pathlib import Path
import sys

directory = Path(__file__).resolve().parent
with (directory / "config.json").open(encoding="utf-8") as config_file:
    config = json.load(config_file)
environment = os.environ.copy()
environment.update(config)
os.execvpe("java", ["java", "-jar", str(directory / "parcel-tracker-backend.jar")], environment)
