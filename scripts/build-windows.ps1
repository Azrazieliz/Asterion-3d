param([string]$Sdk="$env:LOCALAPPDATA\Android\Sdk",[string]$Java='C:\Program Files\Android\Android Studio1\jbr')
$ErrorActionPreference='Continue'
$root=Split-Path $PSScriptRoot -Parent
$src=Join-Path $root 'app\src\main'
$build=Join-Path $root 'app\build\direct'
$tools=Join-Path $Sdk 'build-tools\35.0.1'
$android=Join-Path $Sdk 'platforms\android-35\android.jar'
$env:JAVA_HOME=$Java
$env:PATH="$Java\bin;$env:PATH"
$python='D:\Tools\Python311\python.exe'
$native="$build\offline-native"
& $python "$root\scripts\prepare_native.py" --stage $native
if($LASTEXITCODE -ne 0){throw 'Pinned model/runtime preparation failed'}
$jars=@(Get-ChildItem "$native\jars" -Filter '*.jar' | ForEach-Object {$_.FullName})
$classPath=(@($android)+$jars) -join ';'
New-Item -ItemType Directory -Force -Path "$build\classes","$build\dex","$build\host","$root\dist" | Out-Null
$sourceFiles=@(Get-ChildItem "$src\java" -Recurse -File -Filter '*.java' | ForEach-Object {$_.FullName})
& "$Java\bin\javac.exe" -encoding UTF-8 --release 17 -cp $classPath -d "$build\classes" @sourceFiles
if($LASTEXITCODE -ne 0){throw 'Java compilation failed'}
$core=@('FilesUtil','Mesh','Recipes','Glb','VisualHull','GeometryAudit','DepthRelief','MeshRevision','FormLibrary','SkeletalMotion','ReferenceView','PoseEvidence','SkinBlend','ClothDrape','RigAssembly','DragonRig','PartHull') | ForEach-Object {"$src\java\com\asterion\foundry\$_.java"}
$fixtures=@('CoreAcceptance','FormAcceptance','MotionAcceptance','ReferenceViewAcceptance','PoseEvidenceAcceptance','SkinBlendAcceptance','ScenePartsAcceptance','DragonRigAcceptance','ChoreographyAcceptance','PartHullAcceptance') | ForEach-Object {"$root\tests\$_.java"}
& "$Java\bin\javac.exe" -encoding UTF-8 --release 17 -d "$build\host" @core @fixtures
if($LASTEXITCODE -ne 0){throw 'Host test compilation failed'}
Push-Location $root
foreach($fixture in @('CoreAcceptance','com.asterion.foundry.FormAcceptance','MotionAcceptance','com.asterion.foundry.ReferenceViewAcceptance','com.asterion.foundry.PoseEvidenceAcceptance','com.asterion.foundry.SkinBlendAcceptance','com.asterion.foundry.ScenePartsAcceptance','com.asterion.foundry.DragonRigAcceptance','com.asterion.foundry.ChoreographyAcceptance','com.asterion.foundry.PartHullAcceptance')) {
    & "$Java\bin\java.exe" -cp "$build\host" $fixture
    if($LASTEXITCODE -ne 0){throw "Acceptance failed: $fixture"}
}
& 'D:\Tools\Python311\python.exe' "$root\scripts\validate_glb.py" @(Get-ChildItem "$root\test-output" -Filter '*.glb' | ForEach-Object {$_.FullName})
if($LASTEXITCODE -ne 0){throw 'GLB validation failed'}
Pop-Location
& "$tools\aapt2.exe" compile --dir "$src\res" -o "$build\res.zip"
if($LASTEXITCODE -ne 0){throw 'Resources failed'}
& "$tools\aapt2.exe" link -o "$build\unsigned.apk" --manifest "$src\AndroidManifest.xml" -I $android --min-sdk-version 29 --target-sdk-version 35 --version-code 4 --version-name '0.4.0-production-evaluation' -0 tflite -A "$native\assets" "$build\res.zip"
if($LASTEXITCODE -ne 0){throw 'AAPT failed'}
& "$Java\bin\jar.exe" --create --file "$build\classes.jar" -C "$build\classes" .
if($LASTEXITCODE -ne 0){throw 'Class archive failed'}
& "$tools\d8.bat" --release --min-api 29 --lib $android --output "$build\dex" "$build\classes.jar" @jars
if($LASTEXITCODE -ne 0){throw 'D8 failed'}
& $python "$root\scripts\pack_native_apk.py" "$build\unsigned.apk" "$build\dex" "$native\jni"
if($LASTEXITCODE -ne 0){throw 'DEX insertion failed'}
& "$tools\zipalign.exe" -f -P 16 4 "$build\unsigned.apk" "$build\aligned.apk"
if($LASTEXITCODE -ne 0){throw 'Alignment failed'}
$apk="$root\dist\AsterionFoundry-v0.4.0-EVALUATION.apk"
& "$tools\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $apk "$build\aligned.apk"
if($LASTEXITCODE -ne 0){throw 'Signature failed'}
& "$tools\apksigner.bat" verify --verbose $apk
if($LASTEXITCODE -ne 0){throw 'Signature verification failed'}
& "$tools\zipalign.exe" -c -P 16 4 $apk
if($LASTEXITCODE -ne 0){throw 'APK alignment verification failed'}
& $python "$root\scripts\pack_native_apk.py" --verify $apk
if($LASTEXITCODE -ne 0){throw 'Embedded native model verification failed'}
Get-FileHash $apk -Algorithm SHA256
Write-Output "BUILT_APK=$apk"
