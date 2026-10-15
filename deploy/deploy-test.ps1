<#
  On ikki -> test server (https://dastur-test.online) in one command.

  What it does, in order:
    1. Raises the app version (versionName = -Version, versionCode + 1) in app/build.gradle.kts
    2. Builds the signed release APK (gradle assembleRelease)
    3. Updates deploy/index.html (version, size, date, and the "Yangi" list if -Yangiliklar given)
    4. Uploads on-ikki.apk and index.html to the server with scp
    5. Downloads the APK back from the site and checks it is byte-for-byte the same (SHA-256)
    6. Commits the version bump + page and pushes to GitHub

  Examples (run from the project folder "on ikki"):
    powershell -ExecutionPolicy Bypass -File deploy\deploy-test.ps1 -Version 1.4.0 -DryRun
    powershell -ExecutionPolicy Bypass -File deploy\deploy-test.ps1 -Version 1.4.0 -Yangiliklar "Kundalik","Papkalar va maxfiy qaydlar"

  -DryRun : only checks (versions, files, SSH connection) - changes nothing, uploads nothing.
  -NoGit  : skip the git commit/push at the end.
  The script is ASCII-only on purpose: Windows PowerShell 5.1 misreads non-ASCII .ps1 files.
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string[]]$Yangiliklar = @(),
    [switch]$DryRun,
    [switch]$NoGit
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# ---------------------------------------------------------------- settings
$Root      = Split-Path -Parent $PSScriptRoot
$Server    = 'root@179.198.192.69'
$Key       = Join-Path $HOME '.ssh\zargarlik_vps'
$RemoteDir = '/opt/test-loyiha/onikki'
$SiteUrl   = 'https://dastur-test.online'
$Gradle    = 'C:\Users\user\.gradle\wrapper\dists\gradle-8.14.5-bin\690y85m0j9nfaub7xoiayko8a\gradle-8.14.5\bin\gradle.bat'
$JavaHome  = 'C:\Users\user\.jdks\jbr-21.0.11'
$GradleKts = Join-Path $Root 'app\build.gradle.kts'
$Page      = Join-Path $Root 'deploy\index.html'
$Apk       = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
$Utf8      = New-Object System.Text.UTF8Encoding($false)   # UTF-8 without BOM
$Dot       = [char]0x00B7                                   # the middle dot used on the page

function Step($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }
function Ok($text)   { Write-Host "    OK  $text" -ForegroundColor Green }
function Fail($text) { Write-Host "    XATO: $text" -ForegroundColor Red; exit 1 }
function ReadText($path)        { [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8) }
function WriteText($path, $txt) { [IO.File]::WriteAllText($path, $txt, $Utf8) }

# ---------------------------------------------------------------- 0. checks
Step "Tekshiruv"
if ($Version -notmatch '^\d+\.\d+\.\d+$') { Fail "Versiya 1.4.0 ko'rinishida bo'lsin (hozir: $Version)" }
foreach ($f in @($GradleKts, $Page, $Key, $Gradle)) { if (-not (Test-Path $f)) { Fail "Topilmadi: $f" } }
if (-not (Test-Path (Join-Path $Root 'keystore.properties'))) { Fail "keystore.properties yo'q - release imzosi uchun kerak" }

$kts = ReadText $GradleKts
$oldName = [regex]::Match($kts, 'versionName = "([^"]+)"').Groups[1].Value
$oldCode = [int][regex]::Match($kts, 'versionCode = (\d+)').Groups[1].Value
if ([version]$Version -le [version]$oldName) { Fail "Yangi versiya ($Version) hozirgisidan ($oldName) katta bo'lishi kerak" }
$newCode = $oldCode + 1
Ok "Versiya: $oldName (kod $oldCode) -> $Version (kod $newCode)"

& ssh -i $Key -o BatchMode=yes -o ConnectTimeout=15 $Server "test -d $RemoteDir && echo ready" | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "Serverga SSH bilan ulanib bo'lmadi (kalit: $Key)" }
Ok "Server bilan aloqa bor ($Server)"

if ($DryRun) {
    Write-Host ""
    Write-Host "DryRun: hammasi tayyor. Hech narsa o'zgartirilmadi va yuklanmadi." -ForegroundColor Yellow
    exit 0
}

# ---------------------------------------------------------------- 1. version
Step "1/6 Versiyani oshirish"
$kts = $kts -replace 'versionCode = \d+', "versionCode = $newCode"
$kts = $kts -replace 'versionName = "[^"]+"', "versionName = `"$Version`""
WriteText $GradleKts $kts
Ok "app/build.gradle.kts yangilandi"

# ---------------------------------------------------------------- 2. build
Step "2/6 Release APK yig'ilmoqda (bir necha daqiqa)"
$env:JAVA_HOME = $JavaHome
Push-Location $Root
try {
    & $Gradle assembleRelease -q
    if ($LASTEXITCODE -ne 0) { Fail "Build muvaffaqiyatsiz. Versiyani qaytarish: git checkout app/build.gradle.kts" }
} finally { Pop-Location }
if (-not (Test-Path $Apk)) { Fail "APK topilmadi: $Apk" }
$hash = (Get-FileHash $Apk -Algorithm SHA256).Hash.ToLower()
$sizeMb = [math]::Round((Get-Item $Apk).Length / 1MB, 1).ToString([Globalization.CultureInfo]::InvariantCulture)
Ok "APK tayyor: $sizeMb MB, sha256 $hash"

# ---------------------------------------------------------------- 3. page
Step "3/6 Yuklab olish sahifasi"
$months = @('yanvar', 'fevral', 'mart', 'aprel', 'may', 'iyun', 'iyul', 'avgust', 'sentabr', 'oktabr', 'noyabr', 'dekabr')
$today = Get-Date
$dateUz = "$($today.Day)-$($months[$today.Month - 1]) $($today.Year)"
$html = ReadText $Page
$html = [regex]::Replace($html, "[\d.]+ MB $Dot versiya [\d.]+ $Dot [^<]+", "$sizeMb MB $Dot versiya $Version $Dot $dateUz")
$html = [regex]::Replace($html, '<h2>Yangi: [\d.]+</h2>', "<h2>Yangi: $Version</h2>")
if ($Yangiliklar.Count -gt 0) {
    $items = ($Yangiliklar | ForEach-Object { "      <li>$([Net.WebUtility]::HtmlEncode($_))</li>" }) -join "`n"
    $html = [regex]::Replace($html, "(<h2>Yangi: [\d.]+</h2>\s*<ul[^>]*>)[\s\S]*?(\s*</ul>)", "`$1`n$items`$2")
}
WriteText $Page $html
Ok "deploy/index.html: versiya $Version, $dateUz"

# ---------------------------------------------------------------- 4. upload
Step "4/6 Serverga yuklash"
& scp -i $Key -q $Apk "${Server}:$RemoteDir/on-ikki.apk"
if ($LASTEXITCODE -ne 0) { Fail "APK yuklanmadi" }
& scp -i $Key -q $Page "${Server}:$RemoteDir/index.html"
if ($LASTEXITCODE -ne 0) { Fail "index.html yuklanmadi" }
Ok "on-ikki.apk va index.html yuklandi -> $RemoteDir"

# ---------------------------------------------------------------- 5. verify
Step "5/6 Saytdan tekshirish"
$tmp = Join-Path $env:TEMP 'onikki-check.apk'
Invoke-WebRequest -Uri "$SiteUrl/on-ikki.apk" -OutFile $tmp -UseBasicParsing
$liveHash = (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower()
Remove-Item $tmp -Force
if ($liveHash -ne $hash) { Fail "Saytdagi fayl boshqa! ($liveHash)" }
$pageText = (Invoke-WebRequest -Uri $SiteUrl -UseBasicParsing).Content
if ($pageText -notmatch [regex]::Escape("versiya $Version")) { Fail "Sahifada versiya $Version ko'rinmayapti" }
Ok "Saytdagi APK bir xil, sahifada versiya $Version"

# ---------------------------------------------------------------- 6. git
if ($NoGit) {
    Write-Host ""; Write-Host "Git o'tkazib yuborildi (-NoGit)." -ForegroundColor Yellow
} else {
    Step "6/6 GitHub'ga saqlash"
    Push-Location $Root
    try {
        & git add app/build.gradle.kts deploy/index.html
        & git -c user.name=Rahmatillo -c user.email=rahmatillozokirjonov853@gmail.com commit -q -m "Release $Version (versionCode $newCode)"
        & git push -q origin main
        if ($LASTEXITCODE -ne 0) { Fail "git push bo'lmadi - keyin qo'lda: git push origin main" }
    } finally { Pop-Location }
    Ok "Commit va push qilindi"
}

Write-Host ""
Write-Host "TAYYOR: $SiteUrl  (versiya $Version)" -ForegroundColor Green
