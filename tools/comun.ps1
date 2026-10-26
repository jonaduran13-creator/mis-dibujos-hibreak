# Rutas y datos comunes de los scripts. Se carga con:  . "$PSScriptRoot\tools\comun.ps1"
# Nada personal va en el código: el aparato y la contraseña de la llave se leen del entorno
# o de keystore/keystore.properties (excluido de git).

$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))

# Herramientas: JDK 17 + Android build-tools 30.0.3 + platform android-30.
$Tools = if ($env:HIBREAK_TOOLS) { $env:HIBREAK_TOOLS } else { "C:\tmp\hb" }
$Jdk = (Get-ChildItem -Directory "$Tools\jdk-17*" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
if (-not $Jdk) { throw "No encuentro el JDK 17 en $Tools (define HIBREAK_TOOLS)" }
$env:JAVA_HOME = $Jdk
$env:Path = "$Jdk\bin;$env:Path"
$BuildTools = "$Tools\sdk\build-tools\30.0.3"
$AndroidJar = "$Tools\sdk\platforms\android-30\android.jar"

$Adb = "$env:LOCALAPPDATA\Android\platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $Adb)) { $Adb = "adb" }

# Aparato: -Serial del script, o ANDROID_SERIAL; si no, el único conectado por cable
# (los que tienen ":" son por Wi-Fi, p. ej. una tele, y se ignoran).
function Get-AdbArgs([string]$Serial) {
    if (-not $Serial) { $Serial = $env:ANDROID_SERIAL }
    if (-not $Serial) {
        $all = @(& $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' } | ForEach-Object { ($_ -split "`t")[0] })
        $usb = @($all | Where-Object { $_ -notmatch ':' })
        # Nunca se elige solo un aparato por Wi-Fi: podría ser otro equipo de la casa (una tele).
        if ($usb.Count -eq 0) { throw "No hay ningún aparato conectado por cable (adb ve: $($all -join ', ')). Conecta el teléfono o usa -Serial." }
        if ($usb.Count -gt 1) { throw "Hay varios aparatos por cable ($($usb -join ', ')): usa -Serial o ANDROID_SERIAL" }
        $Serial = $usb[0]
    }
    return @("-s", $Serial)
}

# Llave de firma. Como la app es device owner, TODA actualización de un aparato debe firmarse
# con la misma llave; si no existe, se crea una nueva (solo sirve para instalaciones nuevas).
$Keystore = "$ProjectRoot\keystore\dibujos.keystore"
$KeystoreProps = "$ProjectRoot\keystore\keystore.properties"
function Get-KeystorePassword {
    if ($env:MISDIBUJOS_KS_PASS) { return $env:MISDIBUJOS_KS_PASS }
    if (Test-Path -LiteralPath $KeystoreProps) {
        $line = Get-Content $KeystoreProps | Where-Object { $_ -match '^storePassword=' } | Select-Object -First 1
        if ($line) { return $line.Substring("storePassword=".Length) }
    }
    if (Test-Path -LiteralPath $Keystore) { throw "Falta la contraseña de $Keystore (keystore.properties o MISDIBUJOS_KS_PASS)" }
    New-Item -ItemType Directory -Force (Split-Path $Keystore) | Out-Null
    $pass = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
    & keytool -genkeypair -keystore $Keystore -alias dibujos -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $pass -keypass $pass -dname "CN=Mis Dibujos" | Out-Null
    Set-Content -LiteralPath $KeystoreProps -Value "storePassword=$pass" -Encoding ascii
    Write-Warning "Se creó una llave nueva en $Keystore. Guárdala: sin ella no podrás actualizar la app instalada."
    return $pass
}
