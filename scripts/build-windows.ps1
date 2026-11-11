param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Java = 'C:\Program Files\Android\Android Studio1\jbr',
    [string]$Python = $env:FOUNDRY_PYTHON,
    [string]$SigningKeystore = $env:FOUNDRY_SIGNING_KEYSTORE
)
$ErrorActionPreference='Continue'
$root=Split-Path $PSScriptRoot -Parent
# Reuse the current worker's installed toolchains without relying on an
# obsolete drive letter or a particular Android Studio directory name.
if(-not (Test-Path (Join-Path $Sdk 'platforms\android-35\android.jar'))){
    foreach($candidate in @($env:ANDROID_SDK_ROOT,$env:ANDROID_HOME,"$env:LOCALAPPDATA\Android\Sdk")){
        if($candidate -and (Test-Path (Join-Path $candidate 'platforms\android-35\android.jar'))){
            $Sdk=$candidate; break
        }
    }
}
if(-not (Test-Path (Join-Path $Sdk 'platforms\android-35\android.jar'))){
    throw "Android 35 SDK not found. Set ANDROID_HOME or pass -Sdk."
}
if(-not (Test-Path (Join-Path $Java 'bin\javac.exe'))){
    foreach($candidate in @($env:FOUNDRY_JAVA_HOME,$env:JAVA_HOME,
            'C:\Program Files\Android\Android Studio\jbr',
            'C:\Program Files\Android\Android Studio1\jbr')){
        if($candidate -and (Test-Path (Join-Path $candidate 'bin\javac.exe'))){
            $Java=$candidate;break
        }
    }
}
if(-not (Test-Path (Join-Path $Java 'bin\javac.exe'))){
    $found=Get-Command javac.exe -ErrorAction SilentlyContinue
    if($found -and $found.Source){$Java=Split-Path (Split-Path $found.Source -Parent) -Parent}
}
if(-not (Test-Path (Join-Path $Java 'bin\javac.exe'))){
    throw "Java 17 compiler not found. Set FOUNDRY_JAVA_HOME or pass -Java."
}
if(-not $Python -or -not (Test-Path $Python)){
    foreach($candidate in @('D:\Tools\Python311\python.exe',
            'C:\Python311\python.exe',
            "$env:LOCALAPPDATA\Programs\Python\Python311\python.exe")){
        if($candidate -and (Test-Path $candidate)){$Python=$candidate;break}
    }
}
if(-not $Python -or -not (Test-Path $Python)){
    $found=Get-Command python.exe -ErrorAction SilentlyContinue
    if($found -and $found.Source){$Python=$found.Source}
}
if(-not $Python -or -not (Test-Path $Python)){
    throw "Python 3.11+ not found. Set FOUNDRY_PYTHON or pass -Python."
}
& $Python -c 'import sys; assert sys.version_info >= (3, 11), "Python 3.11 or newer is required"'
if($LASTEXITCODE -ne 0){throw "Foundry Python interpreter is not usable: $Python"}
if(-not $SigningKeystore){$SigningKeystore=Join-Path $env:USERPROFILE '.android\debug.keystore'}
if(-not (Test-Path $SigningKeystore)){
    throw "Original signing keystore absent: $SigningKeystore. Refusing a new signing identity."
}
Write-Output "FOUNDRY_WORKER_TOOLCHAIN Python=$Python Java=$Java SDK=$Sdk"
$src=Join-Path $root 'app\src\main'
$build=Join-Path $root 'app\build\direct'
$tools=Join-Path $Sdk 'build-tools\35.0.1'
$android=Join-Path $Sdk 'platforms\android-35\android.jar'
$env:JAVA_HOME=$Java
$env:PATH="$Java\bin;$env:PATH"
$python=$Python
$native="$build\offline-native"
& $python "$root\scripts\prepare_native.py" --stage $native --require-embedded-models --fetch-embedded-models
if($LASTEXITCODE -ne 0){throw 'Pinned model/runtime preparation failed'}
& $python "$root\tests\test_embedded_model_contract.py"
if($LASTEXITCODE -ne 0){throw 'Pinned model archive contract tests failed'}
$bundle=Join-Path $native 'assets\foundry-3d.foundrymodels'
if(-not (Test-Path $bundle) -or (Get-Item $bundle).Length -lt 600000000){throw 'Full embedded reconstruction archive missing'}
$jars=@(Get-ChildItem "$native\jars" -Filter '*.jar' | ForEach-Object {$_.FullName})
$classPath=(@($android)+$jars) -join ';'
New-Item -ItemType Directory -Force -Path "$build\classes","$build\dex","$build\host","$root\dist" | Out-Null
$sourceFiles=@(Get-ChildItem "$src\java" -Recurse -File -Filter '*.java' | ForEach-Object {$_.FullName})
& "$Java\bin\javac.exe" -encoding UTF-8 --release 17 -cp $classPath -d "$build\classes" @sourceFiles
if($LASTEXITCODE -ne 0){throw 'Java compilation failed'}
$core=@('FilesUtil','Mesh','Recipes','Glb','VisualHull','HullRefinement','MeshThumbnail','GeometryAudit','PrintExport','DepthRelief','MeshRevision','PartSurface','PartMaterials','PartTextureMaps','UvOverlapAudit','PartReplacement','FormLibrary','SkeletalMotion','ReferenceCrop','ReferenceView','PoseEvidence','SkinBlend','ClothDrape','RigAssembly','DragonRig','PartHull') | ForEach-Object {"$src\java\com\asterion\foundry\$_.java"}
$fixtures=@('CoreAcceptance','ComplexPartScaleAcceptance','PrintExportAcceptance','HullRefinementAcceptance','MeshThumbnailAcceptance','PartSurfaceAcceptance','PartMaterialsAcceptance','PartTextureMapsAcceptance','UvOverlapAcceptance','PartReplacementAcceptance','FormAcceptance','MotionAcceptance','ReferenceCropAcceptance','ReferenceViewAcceptance','PoseEvidenceAcceptance','SkinBlendAcceptance','ScenePartsAcceptance','DragonRigAcceptance','ChoreographyAcceptance','PartHullAcceptance') | ForEach-Object {"$root\tests\$_.java"}
& "$Java\bin\javac.exe" -encoding UTF-8 --release 17 -d "$build\host" @core @fixtures
if($LASTEXITCODE -ne 0){throw 'Host test compilation failed'}
Push-Location $root
foreach($fixture in @('CoreAcceptance','com.asterion.foundry.ComplexPartScaleAcceptance','com.asterion.foundry.PrintExportAcceptance','com.asterion.foundry.HullRefinementAcceptance','com.asterion.foundry.MeshThumbnailAcceptance','com.asterion.foundry.PartSurfaceAcceptance','com.asterion.foundry.PartMaterialsAcceptance','com.asterion.foundry.PartTextureMapsAcceptance','com.asterion.foundry.UvOverlapAcceptance','com.asterion.foundry.PartReplacementAcceptance','com.asterion.foundry.FormAcceptance','MotionAcceptance','com.asterion.foundry.ReferenceCropAcceptance','com.asterion.foundry.ReferenceViewAcceptance','com.asterion.foundry.PoseEvidenceAcceptance','com.asterion.foundry.SkinBlendAcceptance','com.asterion.foundry.ScenePartsAcceptance','com.asterion.foundry.DragonRigAcceptance','com.asterion.foundry.ChoreographyAcceptance','com.asterion.foundry.PartHullAcceptance')) {
    & "$Java\bin\java.exe" -cp "$build\host" $fixture
    if($LASTEXITCODE -ne 0){throw "Acceptance failed: $fixture"}
}
$jsonRoot=Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\org.json\json'
$jsonJar=$env:FOUNDRY_JSON_JAR
if([string]::IsNullOrWhiteSpace($jsonJar) -and (Test-Path $jsonRoot)){
    $jsonJar=Get-ChildItem $jsonRoot -Recurse -Filter 'json-*.jar' -File -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}
if(-not [string]::IsNullOrWhiteSpace($jsonJar) -and (Test-Path $jsonJar)){
    Write-Output "STAGE_TEST_JSON_JAR=$jsonJar"
    $stageSources=@('StageManifest','SurfaceAudit') | ForEach-Object {"$src\java\com\asterion\foundry\$_.java"}
    & "$Java\bin\javac.exe" -encoding UTF-8 --release 17 -cp "$build\host;$jsonJar" -d "$build\host" @stageSources "$root\tests\StagePipelineAcceptance.java"
    if($LASTEXITCODE -ne 0){throw 'Staged production regression compilation failed'}
    & "$Java\bin\java.exe" -cp "$build\host;$jsonJar" com.asterion.foundry.StagePipelineAcceptance
    if($LASTEXITCODE -ne 0){throw 'Staged production regression failed'}
}else{
    Write-Warning 'Stage regression skipped because host org.json JAR was not found'
}
& $python "$root\scripts\validate_glb.py" @(Get-ChildItem "$root\test-output" -Filter '*.glb' | ForEach-Object {$_.FullName})
if($LASTEXITCODE -ne 0){throw 'GLB validation failed'}
& $python "$root\scripts\validate_stl.py" "$root\test-output\print_box.stl" "$root\test-output\print_large.stl"
if($LASTEXITCODE -ne 0){throw 'Independent print STL validation failed'}
Pop-Location
& "$tools\aapt2.exe" compile --dir "$src\res" -o "$build\res.zip"
if($LASTEXITCODE -ne 0){throw 'Resources failed'}
& "$tools\aapt2.exe" link -o "$build\unsigned.apk" --manifest "$src\AndroidManifest.xml" -I $android --min-sdk-version 29 --target-sdk-version 35 --version-code 7 --version-name '0.5.2-premium-bundled-models' -0 tflite -0 foundrymodels -A "$native\assets" "$build\res.zip"
if($LASTEXITCODE -ne 0){throw 'AAPT failed'}
& "$Java\bin\jar.exe" --create --file "$build\classes.jar" -C "$build\classes" .
if($LASTEXITCODE -ne 0){throw 'Class archive failed'}
& "$tools\d8.bat" --release --min-api 29 --lib $android --output "$build\dex" "$build\classes.jar" @jars
if($LASTEXITCODE -ne 0){throw 'D8 failed'}
& $python "$root\scripts\pack_native_apk.py" "$build\unsigned.apk" "$build\dex" "$native\jni"
if($LASTEXITCODE -ne 0){throw 'DEX insertion failed'}
& "$tools\zipalign.exe" -f -P 16 4 "$build\unsigned.apk" "$build\aligned.apk"
if($LASTEXITCODE -ne 0){throw 'Alignment failed'}
$apk="$root\dist\AsterionFoundry-v0.5.2-PRODUCTION-DEV.apk"
$candidate="$apk.incoming"
if(Test-Path $candidate){Remove-Item $candidate -Force}
& "$tools\apksigner.bat" sign --ks $SigningKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $candidate "$build\aligned.apk"
if($LASTEXITCODE -ne 0){throw 'Signature failed'}
& "$tools\apksigner.bat" verify --verbose $candidate
if($LASTEXITCODE -ne 0){throw 'Signature verification failed'}
& "$tools\zipalign.exe" -c -P 16 4 $candidate
if($LASTEXITCODE -ne 0){throw 'APK alignment verification failed'}
& $python "$root\scripts\pack_native_apk.py" --verify $candidate
if($LASTEXITCODE -ne 0){throw 'Embedded native model verification failed'}
# A changed debug key would prevent the new app from upgrading the approved
# installed version. Refuse silent certificate rotation or data loss.
$previous=Join-Path $root 'dist\AsterionFoundry-v0.5.1-PRODUCTION-DEV.apk'
if(Test-Path $previous){
    $oldCert=@(& "$tools\apksigner.bat" verify --print-certs $previous)
    if($LASTEXITCODE -ne 0){throw 'Cannot verify previous signed release identity'}
    $newCert=@(& "$tools\apksigner.bat" verify --print-certs $candidate)
    if($LASTEXITCODE -ne 0){throw 'Cannot verify candidate signed release identity'}
    $oldHash=($oldCert|Select-String 'Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]+)'|Select-Object -First 1).Matches.Groups[1].Value
    $newHash=($newCert|Select-String 'Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]+)'|Select-Object -First 1).Matches.Groups[1].Value
    if(-not $oldHash -or -not $newHash -or $oldHash -ne $newHash){
        throw 'Existing v0.5.1 and new APK have different signing certificates; refusing incompatible upgrade'
    }
}
if(Test-Path $apk){
    $previousHash=(Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    $history=Join-Path $root 'dist\history'
    New-Item -ItemType Directory -Force -Path $history|Out-Null
    $archived=Join-Path $history ("AsterionFoundry-v0.5.2-"+$previousHash.Substring(0,16)+".apk")
    if(-not (Test-Path $archived)){Copy-Item $apk $archived}
}
Move-Item -Force $candidate $apk
Get-FileHash $apk -Algorithm SHA256
Write-Output "BUILT_APK=$apk"
