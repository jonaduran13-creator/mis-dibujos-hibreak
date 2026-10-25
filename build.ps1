# Compila, firma y (opcional) instala Mis Dibujos.
#   powershell -ExecutionPolicy Bypass -File build.ps1                        # solo compila
#   powershell -ExecutionPolicy Bypass -File build.ps1 -Install               # compila e instala por adb
#   powershell -ExecutionPolicy Bypass -File build.ps1 -Install -Serial XXXX  # si hay varios aparatos
# Herramientas, aparato y llave: ver tools\comun.ps1.
param([switch]$Install, [string]$Serial)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\tools\comun.ps1"

$src = $PSScriptRoot
$bt  = $BuildTools
$jar = $AndroidJar
$out = "$src\build"
if ([IO.Path]::GetFullPath($out) -ne [IO.Path]::Combine([IO.Path]::GetFullPath($src), "build")) { throw "Ruta de build no válida" }

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\res", "$out\gen", "$out\classes", "$out\dex", "$out\stubs" | Out-Null

& "$bt\aapt2.exe" compile --dir "$src\res" -o "$out\res\res.zip"
if ($LASTEXITCODE) { throw "aapt2 compile" }
& "$bt\aapt2.exe" link -I $jar --manifest "$src\AndroidManifest.xml" --java "$out\gen" -o "$out\unsigned.apk" "$out\res\res.zip"
if ($LASTEXITCODE) { throw "aapt2 link" }

# Firmas de las clases del sistema de Bigme (handwritten.jar): solo para compilar, no se empaquetan.
$stubs = @(Get-ChildItem -Recurse "$src\stubs" -Filter *.java | ForEach-Object FullName)
& javac --release 8 -nowarn -encoding UTF-8 -classpath $jar -d "$out\stubs" $stubs
if ($LASTEXITCODE) { throw "javac stubs" }

$java = @(Get-ChildItem -Recurse "$src\src", "$out\gen" -Filter *.java | ForEach-Object FullName)
& javac --release 8 -nowarn -encoding UTF-8 -classpath "$jar;$out\stubs" -d "$out\classes" $java
if ($LASTEXITCODE) { throw "javac" }

$classes = @(Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object FullName)
& java -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --min-api 30 --lib $jar --output "$out\dex" $classes
if ($LASTEXITCODE) { throw "d8" }

Copy-Item "$out\unsigned.apk" "$out\withdex.apk"
Push-Location "$out\dex"
& jar uf "$out\withdex.apk" classes.dex
Pop-Location

& "$bt\zipalign.exe" -f 4 "$out\withdex.apk" "$out\aligned.apk"
$pass = Get-KeystorePassword
& "$bt\apksigner.bat" sign --ks $Keystore --ks-pass "pass:$pass" --out "$out\MisDibujos.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "apksigner" }
Get-Item "$out\MisDibujos.apk" | Select-Object Name, Length

if ($Install) {
    $device = Get-AdbArgs $Serial
    & $Adb @device install -r "$out\MisDibujos.apk"
    if ($LASTEXITCODE) { throw "No se pudo instalar" }
    # Android conserva la lista anterior de políticas al actualizar un device owner.
    & $Adb @device shell dpm set-active-admin mx.dibujos.kiosko/.AdminReceiver
    if ($LASTEXITCODE) { throw "No se pudo actualizar el permiso de descanso" }
}
