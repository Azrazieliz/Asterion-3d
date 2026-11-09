#!/bin/bash
set -eu
cd "$(dirname "$0")/.."
mkdir -p test-output/classes
javac -d test-output/classes app/src/main/java/com/asterion/foundry/{FilesUtil,Mesh,Recipes,Glb}.java tests/CoreAcceptance.java
java -cp test-output/classes CoreAcceptance
python3 scripts/validate_glb.py test-output/*.glb
