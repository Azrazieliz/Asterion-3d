param([string]$Sdk="$env:LOCALAPPDATA\Android\Sdk",[string]$Java='C:\Program Files\Android\Android Studio1\jbr')
$ErrorActionPreference='Continue'
$root=Split-Path $PSScriptRoot -Parent
$src=Join-Path $root 'app\src\main'
$build=Join-Path $root 'app\build\direct'
$tools=Join-Path $Sdk 'build-tools\35.0.1'
$android=Join-Path $Sdk 'platforms\android-35\android.jar'
$env:JAVA_HOME=$Java
$env:PATH="$Java\bin;$env:PATH"
New-Item -ItemType Directory -Force -Path "$build\classes","$build\dex","$build\host","$root\dist" | Out-Null
$sourceFiles=@(Get-ChildItem "$src\java" -Recurse -File -Filter '*.java' | ForEach-Object {$_.FullName})
& "$Java\bin\javac.exe" -encoding UTF-8 --release 17 -cp $android -d "$build\classes" @sourceFiles
if($LASTEXITCODE -ne 0){throw 'Java compilation failed'}
$core=@('FilesUtil','Mesh','Recipes','Glb') | ForEach-Object {"$src\java\com\asterion\foundry\$_.java"}
& "$Java\bin\javac.exe" -encoding UTF-8 --release 17 -d "$build\host" @core "$root\tests\CoreAcceptance.java"
if($LASTEXITCODE -ne 0){throw 'Host test compilation failed'}
Push-Location $root
& "$Java\bin\java.exe" -cp "$build\host" CoreAcceptance
if($LASTEXITCODE -ne 0){throw 'Core acceptance failed'}
& 'D:\Tools\Python311\python.exe' "$root\scripts\validate_glb.py" @(Get-ChildItem "$root\test-output" -Filter '*.glb' | ForEach-Object {$_.FullName})
if($LASTEXITCODE -ne 0){throw 'GLB validation failed'}
Pop-Location
& "$tools\aapt2.exe" compile --dir "$src\res" -o "$build\res.zip"
if($LASTEXITCODE -ne 0){throw 'Resources failed'}
& "$tools\aapt2.exe" link -o "$build\unsigned.apk" --manifest "$src\AndroidManifest.xml" -I $android --min-sdk-version 29 --target-sdk-version 35 --version-code 1 --version-name '0.1.0-production-slice' "$build\res.zip"
if($LASTEXITCODE -ne 0){throw 'AAPT failed'}
$classFiles=@(Get-ChildItem "$build\classes" -Recurse -Filter '*.class' | ForEach-Object {$_.FullName})
& "$tools\d8.bat" --release --min-api 29 --lib $android --output "$build\dex" @classFiles
if($LASTEXITCODE -ne 0){throw 'D8 failed'}
& 'D:\Tools\Python311\python.exe' -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1],'a');z.write(sys.argv[2],'classes.dex',compress_type=zipfile.ZIP_DEFLATED);z.close()" "$build\unsigned.apk" "$build\dex\classes.dex"
if($LASTEXITCODE -ne 0){throw 'DEX insertion failed'}
& "$tools\zipalign.exe" -f -p 4 "$build\unsigned.apk" "$build\aligned.apk"
if($LASTEXITCODE -ne 0){throw 'Alignment failed'}
$apk="$root\dist\AsterionFoundry-v0.1.0-DEV.apk"
& "$tools\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $apk "$build\aligned.apk"
if($LASTEXITCODE -ne 0){throw 'Signature failed'}
& "$tools\apksigner.bat" verify --verbose $apk
if($LASTEXITCODE -ne 0){throw 'Signature verification failed'}
Get-FileHash $apk -Algorithm SHA256
Write-Output "BUILT_APK=$apk"
