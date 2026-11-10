#!/bin/bash
set -eu
cd "$(dirname "$0")/.."
mkdir -p test-output/classes
javac -d test-output/classes app/src/main/java/com/asterion/foundry/{FilesUtil,Mesh,Recipes,Glb,VisualHull,GeometryAudit,DepthRelief,MeshRevision,FormLibrary,SkeletalMotion,ReferenceView,PoseEvidence}.java tests/{CoreAcceptance,FormAcceptance,MotionAcceptance,ReferenceViewAcceptance,PoseEvidenceAcceptance}.java
java -cp test-output/classes CoreAcceptance
java -cp test-output/classes com.asterion.foundry.FormAcceptance
java -cp test-output/classes MotionAcceptance
java -cp test-output/classes com.asterion.foundry.ReferenceViewAcceptance
java -cp test-output/classes com.asterion.foundry.PoseEvidenceAcceptance
python3 scripts/validate_glb.py test-output/*.glb
if [[ -n "${FOUNDRY_JSON_JAR:-}" ]]; then
  javac -cp "test-output/classes:$FOUNDRY_JSON_JAR" -d test-output/classes \
    app/src/main/java/com/asterion/foundry/{OriginalGlb,NoWorldHandoff,OriginalMaterialVariant}.java \
    tests/{NoWorldHandoffAcceptance,OriginalMaterialVariantAcceptance}.java
  java -cp "test-output/classes:$FOUNDRY_JSON_JAR" com.asterion.foundry.NoWorldHandoffAcceptance
  java -cp "test-output/classes:$FOUNDRY_JSON_JAR" com.asterion.foundry.OriginalMaterialVariantAcceptance
fi
