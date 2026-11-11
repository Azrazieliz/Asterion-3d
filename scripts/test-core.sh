#!/bin/bash
set -eu
cd "$(dirname "$0")/.."
mkdir -p test-output/classes
javac -d test-output/classes app/src/main/java/com/asterion/foundry/{FilesUtil,Mesh,Recipes,Glb,VisualHull,HullRefinement,MeshThumbnail,GeometryAudit,PrintExport,DepthRelief,MeshRevision,PartSurface,PartMaterials,PartTextureMaps,UvOverlapAudit,PartReplacement,FormLibrary,SkeletalMotion,ReferenceCrop,AssetStagePolicy,ReferenceView,PoseEvidence,SkinBlend,ClothDrape,RigAssembly,DragonRig,PartHull}.java tests/{CoreAcceptance,ComplexPartScaleAcceptance,PrintExportAcceptance,HullRefinementAcceptance,MeshThumbnailAcceptance,PartSurfaceAcceptance,PartMaterialsAcceptance,PartTextureMapsAcceptance,UvOverlapAcceptance,PartReplacementAcceptance,FormAcceptance,MotionAcceptance,ReferenceCropAcceptance,AssetStagePolicyAcceptance,ReferenceViewAcceptance,PoseEvidenceAcceptance,SkinBlendAcceptance,ScenePartsAcceptance,DragonRigAcceptance,ChoreographyAcceptance,PartHullAcceptance}.java
java -cp test-output/classes CoreAcceptance
java -cp test-output/classes com.asterion.foundry.ComplexPartScaleAcceptance
java -cp test-output/classes com.asterion.foundry.PrintExportAcceptance
java -cp test-output/classes com.asterion.foundry.HullRefinementAcceptance
java -cp test-output/classes com.asterion.foundry.MeshThumbnailAcceptance
java -cp test-output/classes com.asterion.foundry.PartSurfaceAcceptance
java -cp test-output/classes com.asterion.foundry.PartMaterialsAcceptance
java -cp test-output/classes com.asterion.foundry.PartTextureMapsAcceptance
java -cp test-output/classes com.asterion.foundry.UvOverlapAcceptance
java -cp test-output/classes com.asterion.foundry.PartReplacementAcceptance
java -cp test-output/classes com.asterion.foundry.FormAcceptance
java -cp test-output/classes MotionAcceptance
java -cp test-output/classes com.asterion.foundry.ReferenceCropAcceptance
java -cp test-output/classes com.asterion.foundry.AssetStagePolicyAcceptance
java -cp test-output/classes com.asterion.foundry.ReferenceViewAcceptance
java -cp test-output/classes com.asterion.foundry.PoseEvidenceAcceptance
java -cp test-output/classes com.asterion.foundry.SkinBlendAcceptance
java -cp test-output/classes com.asterion.foundry.ScenePartsAcceptance
java -cp test-output/classes com.asterion.foundry.DragonRigAcceptance
java -cp test-output/classes com.asterion.foundry.ChoreographyAcceptance
java -cp test-output/classes com.asterion.foundry.PartHullAcceptance
python3 scripts/validate_glb.py test-output/*.glb
python3 scripts/validate_stl.py test-output/print_box.stl test-output/print_large.stl
if [[ -n "${FOUNDRY_JSON_JAR:-}" ]]; then
  javac -cp "test-output/classes:$FOUNDRY_JSON_JAR" -d test-output/classes \
    app/src/main/java/com/asterion/foundry/{OriginalGlb,NoWorldHandoff,OriginalMaterialVariant,StageManifest,SurfaceAudit}.java \
    tests/{NoWorldHandoffAcceptance,OriginalMaterialVariantAcceptance,StagePipelineAcceptance}.java
  java -cp "test-output/classes:$FOUNDRY_JSON_JAR" com.asterion.foundry.NoWorldHandoffAcceptance
  java -cp "test-output/classes:$FOUNDRY_JSON_JAR" com.asterion.foundry.OriginalMaterialVariantAcceptance
  java -cp "test-output/classes:$FOUNDRY_JSON_JAR" com.asterion.foundry.StagePipelineAcceptance
fi
