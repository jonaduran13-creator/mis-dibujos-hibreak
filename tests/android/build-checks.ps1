# Compila el paquete de pruebas mx.dibujos.editchecks (primero compila el proyecto con build.ps1).
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '..\..\tools\comun.ps1')
$project=$ProjectRoot
$testSource=$PSScriptRoot
$test=Join-Path $PSScriptRoot 'build'
$bt=$BuildTools
$android=$AndroidJar
New-Item -ItemType Directory -Force "$test\classes","$test\dex" | Out-Null
& "$bt\aapt2.exe" link -I $android --manifest "$testSource\AndroidManifest.xml" -o "$test\unsigned.apk"
if($LASTEXITCODE){throw 'aapt2 de pruebas'}
& javac --release 8 -encoding UTF-8 -classpath "$android;$project\build\classes" -d "$test\classes" "$testSource\src\mx\dibujos\kiosko\EditChecks.java"
if($LASTEXITCODE){throw 'javac de pruebas'}
$classes=@(Get-ChildItem -Recurse "$project\build\classes","$test\classes" -Filter *.class | ForEach-Object FullName)
& java -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --min-api 30 --lib $android --output "$test\dex" $classes
if($LASTEXITCODE){throw 'd8 de pruebas'}
Copy-Item -LiteralPath "$test\unsigned.apk" -Destination "$test\withdex.apk" -Force
Push-Location "$test\dex"
& jar uf "$test\withdex.apk" classes.dex
Pop-Location
& "$bt\zipalign.exe" -f 4 "$test\withdex.apk" "$test\aligned.apk"
$pass=Get-KeystorePassword
& java -jar "$bt\lib\apksigner.jar" sign --ks $Keystore --ks-pass "pass:$pass" --out "$test\checks.apk" "$test\aligned.apk"
if($LASTEXITCODE){throw 'firma de pruebas'}
