function Find-ProjectMaven {
    $installedMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($installedMaven) { return $installedMaven.Source }
    $wrapperRoot = Join-Path $env:USERPROFILE '.m2\wrapper\dists'
    if (Test-Path -LiteralPath $wrapperRoot) {
        $cachedMaven = Get-ChildItem -LiteralPath $wrapperRoot -Filter 'mvn.cmd' -File -Recurse |
            Sort-Object FullName -Descending | Select-Object -First 1
        if ($cachedMaven) { return $cachedMaven.FullName }
    }
    throw 'Maven 3.9 or later is required to rebuild. The packaged JAR only needs Java 21.'
}
