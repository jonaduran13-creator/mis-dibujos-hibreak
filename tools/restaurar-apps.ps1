# Vuelve a instalar (y activar) en el aparato los paquetes de apps-quitadas.txt.
#   powershell -ExecutionPolicy Bypass -File tools\restaurar-apps.ps1            # los regresa
#   powershell -ExecutionPolicy Bypass -File tools\restaurar-apps.ps1 -Quitar    # los desinstala (usuario 0)
# La lista es para el Bigme HiBreak con Android 11; revísala antes de usarla en otro aparato.
param([switch]$Quitar, [string]$Serial)
. "$PSScriptRoot\comun.ps1"
$device = Get-AdbArgs $Serial
$lista = Get-Content "$PSScriptRoot\apps-quitadas.txt" | Where-Object { $_ -and -not $_.StartsWith("#") }
foreach ($p in $lista) {
    if ($Quitar) {
        $r = & $Adb @device shell pm uninstall --user 0 $p
    } else {
        $r = & $Adb @device shell cmd package install-existing $p
        & $Adb @device shell pm enable $p | Out-Null
    }
    "{0}: {1}" -f $p, ($r -join " ")
}
