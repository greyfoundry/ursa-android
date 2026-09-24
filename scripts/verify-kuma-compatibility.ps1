[CmdletBinding()]
param(
    [int]$Port = 3040,
    [string[]]$Versions = @(),
    [switch]$SkipWriteProbe
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$images = @(
    [pscustomobject]@{ Version = "2.4.0"; ReportedVersion = "2.4.0"; Digest = "sha256:91e963bfda569ba115206e843febb446f473ab525add4e08b2b9e3beffa16985" },
    [pscustomobject]@{ Version = "2.5.0"; ReportedVersion = "2.5.0"; Digest = "sha256:a8610b3b4c38077922ba51b036691e06887d7cefd91fe620fd3d6d23d03dc240" },
    [pscustomobject]@{ Version = "2.5.1"; ReportedVersion = "2.5.0"; Digest = "sha256:ecd5b8c8b49fe9436c735de9c72b161fcc2b6d8710599393d369a2f6e0167d02" },
    [pscustomobject]@{ Version = "2.5.2"; ReportedVersion = "2.5.1"; Digest = "sha256:68ef1413af569e3e480ae4a04edf5a884255a78736241e0bf7e2b2980ac1327c" },
    [pscustomobject]@{ Version = "2.5.3"; ReportedVersion = "2.5.3"; Digest = "sha256:3e24e96c89efff0e3a4b0698cbdd36c15ad3022371db57166e5588853002ee5c" },
    [pscustomobject]@{ Version = "2.5.4"; ReportedVersion = "2.5.4"; Digest = "sha256:917318f9d7be5257f43ba412c766a473be336eb451d70744f3b482d0c3997c0e" },
    [pscustomobject]@{ Version = "2.5.5"; ReportedVersion = "2.5.5"; Digest = "sha256:c74379ac4509ce2d2c2633f509e67003ee2e45b6e995c5e43fc101f45a0e1fbe" }
)

$recordSeparator = [char]0x1e
$ackId = 0
$socketBase = $null
$socketId = $null
$probePassword = "UrsaMatrix-$([guid]::NewGuid().ToString('N'))!"

function Invoke-Docker {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $output = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return $output
}

function Send-PollingPacket {
    param([Parameter(Mandatory)][string]$Packet)
    Invoke-WebRequest -UseBasicParsing -Method Post -ContentType "text/plain;charset=UTF-8" `
        -Body $Packet -Uri "$socketBase/socket.io/?EIO=4&transport=polling&sid=$socketId" | Out-Null
}

function Receive-PollingPackets {
    $content = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 `
        -Uri "$socketBase/socket.io/?EIO=4&transport=polling&sid=$socketId").Content
    $packets = @()
    foreach ($packet in $content.Split($recordSeparator, [StringSplitOptions]::RemoveEmptyEntries)) {
        if ($packet -eq "2") {
            Send-PollingPacket "3"
        } else {
            $packets += $packet
        }
    }
    return $packets
}

function Open-KumaSocket {
    param([Parameter(Mandatory)][string]$BaseUrl)
    $script:socketBase = $BaseUrl
    $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $open = (Invoke-WebRequest -UseBasicParsing `
        -Uri "$BaseUrl/socket.io/?EIO=4&transport=polling&t=$stamp").Content
    if (-not $open.StartsWith("0")) {
        throw "Unexpected Engine.IO open packet: $open"
    }
    $script:socketId = ($open.Substring(1) | ConvertFrom-Json).sid
    Send-PollingPacket "40"
    return @(Receive-PollingPackets)
}

function Invoke-KumaEvent {
    param(
        [Parameter(Mandatory)][string]$Event,
        [object[]]$Arguments = @()
    )
    $script:ackId++
    $id = $script:ackId
    $payload = @($Event) + $Arguments
    $json = ConvertTo-Json -InputObject $payload -Depth 30 -Compress
    Send-PollingPacket "42$id$json"
    $seen = @()
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $packets = @(Receive-PollingPackets)
        $seen += $packets
        $ack = $packets | Where-Object { $_.StartsWith("43$id") } | Select-Object -First 1
        if ($null -ne $ack) {
            $values = $ack.Substring(("43$id").Length) | ConvertFrom-Json
            return [pscustomobject]@{ Ack = $values[0]; Packets = $seen }
        }
    }
    throw "No acknowledgement received for $Event"
}

function Find-SocketEvent {
    param(
        [Parameter(Mandatory)][object[]]$Packets,
        [Parameter(Mandatory)][string]$Event
    )
    foreach ($packet in $Packets) {
        if (-not $packet.StartsWith("42[")) { continue }
        $values = $packet.Substring(2) | ConvertFrom-Json
        if ($values[0] -eq $Event) { return $values[1] }
    }
    return $null
}

function Assert-Ok {
    param(
        [Parameter(Mandatory)][object]$Result,
        [Parameter(Mandatory)][string]$Operation
    )
    if ($Result.Ack.ok -ne $true) {
        throw "$Operation failed: $($Result.Ack | ConvertTo-Json -Compress)"
    }
}

function Wait-KumaHealthy {
    param([Parameter(Mandatory)][string]$Container)
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $status = (& docker inspect --format '{{.State.Health.Status}}' $Container 2>$null)
        if ($status -eq "healthy") { return }
        if ($status -eq "unhealthy") { throw "$Container became unhealthy" }
        Start-Sleep -Seconds 1
    }
    throw "$Container did not become healthy"
}

function Invoke-FloorWriteProbe {
    $monitor = [ordered]@{
        type = "manual"; name = "URSA compatibility probe"; description = "Temporary matrix check"
        parent = $null; url = ""; method = "GET"; interval = 60; retryInterval = 60
        resendInterval = 0; maxretries = 0; retryOnlyOnStatusCodeFailure = $false
        notificationIDList = [ordered]@{}; ignoreTls = $false; upsideDown = $false
        expiryNotification = $false; domainExpiryNotification = $true; maxredirects = 10
        accepted_statuscodes = @("200-299"); saveResponse = $false; saveErrorResponse = $true
        responseMaxLength = 1024; dns_resolve_type = "A"; dns_resolve_server = ""
        kafkaProducerBrokers = @(); kafkaProducerSaslOptions = [ordered]@{ mechanism = "None" }
        rabbitmqNodes = @(); conditions = @(); active = $false; timeout = 48; manual_status = 1
    }
    $added = Invoke-KumaEvent "add" @(,$monitor)
    Assert-Ok $added "add monitor"
    $monitorId = [int]$added.Ack.monitorID
    try {
        Assert-Ok (Invoke-KumaEvent "pauseMonitor" @($monitorId)) "pause monitor"
        Assert-Ok (Invoke-KumaEvent "resumeMonitor" @($monitorId)) "resume monitor"
        $full = Invoke-KumaEvent "getMonitor" @($monitorId)
        Assert-Ok $full "get monitor"
        $full.Ack.monitor.description = "Round-trip matrix check"
        Assert-Ok (Invoke-KumaEvent "editMonitor" @(,$full.Ack.monitor)) "edit monitor"

        $maintenance = [ordered]@{
            title = "URSA compatibility probe"; description = "Temporary matrix check"
            strategy = "manual"; active = $false; timezoneOption = "SAME_AS_SERVER"
            intervalDay = 1; cron = "30 3 * * *"; durationMinutes = 60
            dateRange = @($null, $null)
            timeRange = @([ordered]@{ hours = 2; minutes = 0 }, [ordered]@{ hours = 3; minutes = 0 })
            weekdays = @(); daysOfMonth = @()
        }
        $maintenanceAdded = Invoke-KumaEvent "addMaintenance" @(,$maintenance)
        Assert-Ok $maintenanceAdded "add maintenance"
        $maintenanceId = [int]$maintenanceAdded.Ack.maintenanceID
        try {
            Assert-Ok (Invoke-KumaEvent "addMonitorMaintenance" @($maintenanceId, @([ordered]@{ id = $monitorId }))) "assign maintenance"
            Assert-Ok (Invoke-KumaEvent "pauseMaintenance" @($maintenanceId)) "pause maintenance"
            Assert-Ok (Invoke-KumaEvent "resumeMaintenance" @($maintenanceId)) "resume maintenance"
        } finally {
            Assert-Ok (Invoke-KumaEvent "deleteMaintenance" @($maintenanceId)) "delete maintenance"
        }

        $notification = [ordered]@{
            name = "URSA compatibility probe"; type = "webhook"; isDefault = $false
            applyExisting = $false; webhookURL = "https://example.invalid/ursa-matrix"
            httpMethod = "post"; webhookContentType = "json"
        }
        $notificationAdded = Invoke-KumaEvent "addNotification" @($notification, $null)
        Assert-Ok $notificationAdded "add notification"
        Assert-Ok (Invoke-KumaEvent "deleteNotification" @([int]$notificationAdded.Ack.id)) "delete notification"
    } finally {
        Assert-Ok (Invoke-KumaEvent "deleteMonitor" @($monitorId, $false)) "delete monitor"
    }
}

$results = @()
foreach ($image in $images) {
    if ($Versions.Count -gt 0 -and $Versions -notcontains $image.Version) { continue }
    $suffix = $image.Version.Replace(".", "")
    $container = "ursa-kuma-matrix-$suffix"
    $volume = "$container-data"
    $reference = "louislam/uptime-kuma@$($image.Digest)"
    & docker rm -f $container 2>$null | Out-Null
    & docker volume rm $volume 2>$null | Out-Null
    try {
        Invoke-Docker @("pull", $reference) | Out-Null
        Invoke-Docker @("volume", "create", $volume) | Out-Null
        Invoke-Docker @(
            "run", "--rm", "-v", "${volume}:/app/data", "--entrypoint", "sh", $reference,
            "-c", 'printf ''{"type":"sqlite"}'' > /app/data/db-config.json'
        ) | Out-Null
        Invoke-Docker @(
            "run", "-d", "--name", $container, "-p", "127.0.0.1:${Port}:3001",
            "-v", "${volume}:/app/data", $reference
        ) | Out-Null
        Wait-KumaHealthy $container

        $initial = @(Open-KumaSocket "http://127.0.0.1:$Port")
        $setup = Invoke-KumaEvent "setup" @("ursa_matrix", $probePassword)
        Assert-Ok $setup "setup"

        $initial = @(Open-KumaSocket "http://127.0.0.1:$Port")
        $loginPayload = [ordered]@{ username = "ursa_matrix"; password = $probePassword; token = "" }
        $login = Invoke-KumaEvent "login" @(,$loginPayload)
        Assert-Ok $login "login"
        $info = Find-SocketEvent $login.Packets "info"
        $monitorTypes = Find-SocketEvent $login.Packets "monitorTypeList"
        if ($null -eq $info -or $info.version -ne $image.ReportedVersion) {
            throw "Expected info.version $($image.ReportedVersion), received $($info.version)"
        }
        $typeNames = @($monitorTypes.PSObject.Properties.Name)
        $parsedVersion = [version]$image.Version
        if (($parsedVersion -ge [version]"2.5.0") -and ($typeNames -notcontains "ntp" -or $typeNames -notcontains "pm2")) {
            throw "$($image.Version) did not advertise NTP and PM2"
        }
        if (($parsedVersion -lt [version]"2.5.4") -and $typeNames -contains "sftp") {
            throw "$($image.Version) unexpectedly advertised SFTP"
        }
        if (($parsedVersion -ge [version]"2.5.4") -and $typeNames -notcontains "sftp") {
            throw "$($image.Version) did not advertise SFTP"
        }
        $writes = "not-run"
        if ($image.Version -eq "2.4.0" -and -not $SkipWriteProbe) {
            Invoke-FloorWriteProbe
            $writes = "passed"
        }
        $results += [pscustomobject]@{
            Version = $image.Version
            Digest = $image.Digest
            InfoVersion = $info.version
            NtpPm2 = ($typeNames -contains "ntp" -and $typeNames -contains "pm2")
            Sftp = $typeNames -contains "sftp"
            FloorWrites = $writes
        }
    } finally {
        & docker rm -f $container 2>$null | Out-Null
        & docker volume rm $volume 2>$null | Out-Null
    }
}

$results | ConvertTo-Json -Depth 4
