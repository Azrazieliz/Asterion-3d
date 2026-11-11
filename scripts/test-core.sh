#!/bin/bash
set -eu
cd "$(dirname "$0")/.."
mkdir -p test-output/classes
javac -d test-output/classes app/src/main/java/com/asterion/foundry/{FilesUtil,Mesh,Recipes,Glb,VisualHull,GeometryAudit,DepthRelief,MeshRevision,PartSurface,PartMaterials,FormLibrary,SkeletalMotion,ReferenceView,PoseEvidence,SkinBlend,ClothDrape,RigAssembly,DragonRig,PartHull}.java tests/{CoreAcceptance,PartSurfaceAcceptance,PartMaterialsAcceptance,FormAcceptance,MotionAcceptance,ReferenceViewAcceptance,PoseEvidenceAcceptance,SkinBlendAcceptance,ScenePartsAcceptance,DragonRigAcceptance,ChoreographyAcceptance,PartHullAcceptance}.java
java -cp test-output/classes CoreAcceptance
java -cp test-output/classes com.asterion.foundry.PartSurfaceAcceptance
java -cp test-output/classes com.asterion.foundry.PartMaterialsAcceptance
java -cp test-output/classes com.asterion.foundry.FormAcceptance
java -cp test-output/classes MotionAcceptance
java -cp test-output/classes com.asterion.foundry.ReferenceViewAcceptance
java -cp test-output/classes com.asterion.foundry.PoseEvidenceAcceptance
java -cp test-output/classes com.asterion.foundry.SkinBlendAcceptance
java -cp test-output/classes com.asterion.foundry.ScenePartsAcceptance
java -cp test-output/classes com.asterion.foundry.DragonRigAcceptance
java -cp test-output/classes com.asterion.foundry.ChoreographyAcceptance
java -cp test-output/classes com.asterion.foundry.PartHullAcceptance
python3 scripts/validate_glb.py test-output/*.glb
if [[ -n "${FOUNDRY_JSON_JAR:-}" ]]; then
  javac -cp "test-output/classes:$FOUNDRY_JSON_JAR" -d test-output/classes \
    app/src/main/java/com/asterion/foundry/{OriginalGlb,NoWorldHandoff,OriginalMaterialVariant,StageManifest,SurfaceAudit}.java \
    tests/{NoWorldHandoffAcceptance,OriginalMaterialVariantAcceptance,StagePipelineAcceptance}.java
  java -cp "test-output/classes:$FOUNDRY_JSON_JAR" com.asterion.foundry.NoWorldHandoffAcceptance
  java -cp "test-output/classes:$FOUNDRY_JSON_JAR" com.asterion.foundry.OriginalMaterialVariantAcceptance
  java -cp "test-output/classes:$FOUNDRY_JSON_JAR" com.asterion.foundry.StagePipelineAcceptance
fi
