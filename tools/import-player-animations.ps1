param(
    [Parameter(Mandatory = $true)]
    [string] $SourceGlb,

    [Parameter(Mandatory = $true)]
    [string] $Destination,

    [string] $CombatCueDestination,

    [string[]] $ModelSources
)

$ErrorActionPreference = 'Stop'

$bytes = [IO.File]::ReadAllBytes($SourceGlb)
$invalidHeader = $bytes.Length -lt 28 `
    -or ([Text.Encoding]::ASCII.GetString($bytes, 0, 4)) -ne 'glTF' `
    -or ([BitConverter]::ToUInt32($bytes, 4)) -ne 2
if ($invalidHeader) {
    throw "Expected a glTF 2.0 GLB: $SourceGlb"
}

$declaredLength = [BitConverter]::ToUInt32($bytes, 8)
if ($declaredLength -ne $bytes.Length) {
    throw "GLB length header does not match the file length"
}

$jsonLength = [BitConverter]::ToUInt32($bytes, 12)
$jsonType = [BitConverter]::ToUInt32($bytes, 16)
if ($jsonType -ne 0x4E4F534A) {
    throw "The first GLB chunk is not JSON"
}
$json = [Text.Encoding]::UTF8.GetString($bytes, 20, [int] $jsonLength).TrimEnd(' ', [char] 0)
$document = $json | ConvertFrom-Json

$binHeader = 20 + [int] $jsonLength
$binLength = [BitConverter]::ToUInt32($bytes, $binHeader)
$binType = [BitConverter]::ToUInt32($bytes, $binHeader + 4)
if ($binType -ne 0x004E4942) {
    throw "The second GLB chunk is not BIN"
}
$declaredBufferLength = [int] $document.buffers[0].byteLength
if ($declaredBufferLength -gt $binLength) {
    throw "The declared glTF buffer exceeds the BIN chunk"
}

$requiredAnimations = @('stand', 'move', 'run', 'jump', 'dash')
$animationsByName = @{}
foreach ($animation in $document.animations) {
    $animationsByName[$animation.name] = $animation
}
foreach ($name in $requiredAnimations) {
    if (-not $animationsByName.ContainsKey($name)) {
        throw "Missing required animation '$name'"
    }
}

$expectedNodes = @(
    'head', 'right_hand', 'right_arm', 'left_hand', 'left_arm', 'chest',
    'torso', 'right_shin', 'right_leg', 'left_shin', 'left_leg', 'bone', 'armature'
)
if ($document.nodes.Count -ne $expectedNodes.Count) {
    throw "Unexpected animation skeleton node count: $($document.nodes.Count)"
}
for ($index = 0; $index -lt $expectedNodes.Count; $index++) {
    if ($document.nodes[$index].name -ne $expectedNodes[$index]) {
        throw "Animation node $index is '$($document.nodes[$index].name)', expected '$($expectedNodes[$index])'"
    }
}

function Get-NodeParentIndices($nodes) {
    $parents = @(-1) * $nodes.Count
    for ($parent = 0; $parent -lt $nodes.Count; $parent++) {
        foreach ($child in @($nodes[$parent].children)) {
            $parents[[int] $child] = $parent
        }
    }
    return ,$parents
}

function Get-TrsComponents($node, [string] $property, [double[]] $defaults) {
    $value = $node.$property
    if ($null -eq $value) {
        return ,$defaults
    }
    return ,@($value | ForEach-Object { [double] $_ })
}

function Assert-NearArray(
    [double[]] $expected,
    [double[]] $actual,
    [string] $location
) {
    if ($expected.Count -ne $actual.Count) {
        throw "$location component count differs"
    }
    for ($index = 0; $index -lt $expected.Count; $index++) {
        if ([Math]::Abs($expected[$index] - $actual[$index]) -gt 0.000001) {
            throw "$location differs at component $index"
        }
    }
}

function Assert-SharedRig($model, [string] $modelPath) {
    if ($model.nodes.Count -ne $document.nodes.Count) {
        throw "$modelPath node count differs from the animation GLB"
    }
    $animationParents = Get-NodeParentIndices $document.nodes
    $modelParents = Get-NodeParentIndices $model.nodes
    for ($index = 0; $index -lt $document.nodes.Count; $index++) {
        $expected = $document.nodes[$index]
        $actual = $model.nodes[$index]
        $location = "$modelPath nodes[$index]"
        if ($actual.name -ne $expected.name) {
            throw "$location is '$($actual.name)', expected '$($expected.name)'"
        }
        if ($modelParents[$index] -ne $animationParents[$index]) {
            throw "$location parent differs from the animation GLB"
        }
        Assert-NearArray `
            (Get-TrsComponents $expected 'translation' @(0.0, 0.0, 0.0)) `
            (Get-TrsComponents $actual 'translation' @(0.0, 0.0, 0.0)) `
            "$location translation"
        Assert-NearArray `
            (Get-TrsComponents $expected 'rotation' @(0.0, 0.0, 0.0, 1.0)) `
            (Get-TrsComponents $actual 'rotation' @(0.0, 0.0, 0.0, 1.0)) `
            "$location rotation"
        Assert-NearArray `
            (Get-TrsComponents $expected 'scale' @(1.0, 1.0, 1.0)) `
            (Get-TrsComponents $actual 'scale' @(1.0, 1.0, 1.0)) `
            "$location scale"
    }
    if ($null -eq $model.skins -or @($model.skins).Count -ne 1) {
        throw "$modelPath must contain exactly one skin"
    }
}

if ($ModelSources) {
    if ($ModelSources.Count -ne 2) {
        throw 'ModelSources must contain the Wild and Sile glTF paths'
    }
    $models = foreach ($modelPath in $ModelSources) {
        $model = Get-Content -LiteralPath $modelPath -Raw | ConvertFrom-Json
        Assert-SharedRig $model $modelPath
        $model
    }
    $canonicalJoints = @($models[0].skins[0].joints)
    $candidateJoints = @($models[1].skins[0].joints)
    if (($canonicalJoints -join ',') -ne ($candidateJoints -join ',')) {
        throw "$($ModelSources[1]) skin joint order differs from $($ModelSources[0])"
    }
}

$animationDirectory = Join-Path $Destination 'animations'
[IO.Directory]::CreateDirectory($animationDirectory) | Out-Null

$buffer = [byte[]]::new($declaredBufferLength)
[Buffer]::BlockCopy($bytes, $binHeader + 8, $buffer, 0, $declaredBufferLength)
[IO.File]::WriteAllBytes((Join-Path $animationDirectory 'player_animation.bin'), $buffer)

$utf8 = [Text.UTF8Encoding]::new($false)
$sidecarNodes = @($document.nodes | ForEach-Object { [ordered]@{ name = $_.name } })
foreach ($name in $requiredAnimations) {
    $sidecar = [ordered]@{
        asset = [ordered]@{
            version = '2.0'
            generator = 'Project Crystal Fracture player animation importer'
        }
        nodes = $sidecarNodes
        buffers = @([ordered]@{
            byteLength = $declaredBufferLength
            uri = 'player_animation.bin'
        })
        bufferViews = @($document.bufferViews)
        accessors = @($document.accessors)
        animations = @($animationsByName[$name])
    }
    $sidecarPath = Join-Path $animationDirectory "$name.animation.gltf.json"
    $sidecarJson = $sidecar | ConvertTo-Json -Depth 100 -Compress
    [IO.File]::WriteAllText($sidecarPath, $sidecarJson + [Environment]::NewLine, $utf8)
}

function Read-FloatAccessor([int] $accessorIndex) {
    $accessor = $document.accessors[$accessorIndex]
    if ($accessor.componentType -ne 5126) {
        throw "Root motion accessor $accessorIndex must use FLOAT components"
    }
    $componentCount = switch ($accessor.type) {
        'SCALAR' { 1 }
        'VEC3' { 3 }
        'VEC4' { 4 }
        default { throw "Unsupported root motion accessor type '$($accessor.type)'" }
    }
    $view = $document.bufferViews[$accessor.bufferView]
    $viewOffset = if ($null -eq $view.byteOffset) { 0 } else { [int] $view.byteOffset }
    $accessorOffset = if ($null -eq $accessor.byteOffset) { 0 } else { [int] $accessor.byteOffset }
    $stride = if ($null -eq $view.byteStride) { $componentCount * 4 } else { [int] $view.byteStride }
    $values = @()
    for ($element = 0; $element -lt $accessor.count; $element++) {
        $components = @()
        $elementOffset = $viewOffset + $accessorOffset + $element * $stride
        for ($component = 0; $component -lt $componentCount; $component++) {
            $components += [BitConverter]::ToSingle($buffer, $elementOffset + $component * 4)
        }
        $values += ,$components
    }
    return ,$values
}

if ($CombatCueDestination) {
    $dash = $animationsByName['dash']
    $metadata = $dash.extras.combatcue
    if ($null -eq $metadata -or $metadata.root_motion.enabled -ne $true) {
        throw "The dash animation must declare enabled CombatCue root motion"
    }
    $rootBone = [string] $metadata.root_motion.bone
    $rootNode = -1
    for ($index = 0; $index -lt $document.nodes.Count; $index++) {
        if ($document.nodes[$index].name -eq $rootBone) {
            $rootNode = $index
            break
        }
    }
    if ($rootNode -lt 0) {
        throw "CombatCue root motion bone '$rootBone' does not exist"
    }
    $translationChannel = $dash.channels | Where-Object {
        $_.target.node -eq $rootNode -and $_.target.path -eq 'translation'
    } | Select-Object -First 1
    if ($null -eq $translationChannel) {
        throw "Dash animation has no translation channel for root bone '$rootBone'"
    }
    $translationSampler = $dash.samplers[$translationChannel.sampler]
    $times = Read-FloatAccessor ([int] $translationSampler.input)
    $translations = Read-FloatAccessor ([int] $translationSampler.output)
    if ($times.Count -ne $translations.Count) {
        throw "Dash root motion time/value counts differ"
    }
    $origin = $translations[0]
    $keyframes = for ($index = 0; $index -lt $times.Count; $index++) {
        [ordered]@{
            time = $times[$index][0]
            x = $translations[$index][0] - $origin[0]
            y = $translations[$index][1] - $origin[1]
            z = $translations[$index][2] - $origin[2]
            yaw = 0.0
        }
    }
    $duration = ($times | ForEach-Object { $_[0] } | Measure-Object -Maximum).Maximum
    $combatCue = [ordered]@{
        schema_version = 4
        duration = $duration
        skeleton = $metadata.skeleton
        sections = @([ordered]@{ id = 'main'; start = 0.0; end = $duration })
        events = @($metadata.events)
        states = @($metadata.states)
        root_motion = [ordered]@{
            enabled = $true
            bone = $rootBone
            mode = [string] $metadata.root_motion.mode
            keyframes = @($keyframes)
        }
        loop = $false
    }
    [IO.Directory]::CreateDirectory($CombatCueDestination) | Out-Null
    $combatCuePath = Join-Path $CombatCueDestination 'dash.combat.json'
    [IO.File]::WriteAllText(
        $combatCuePath,
        ($combatCue | ConvertTo-Json -Depth 100) + [Environment]::NewLine,
        $utf8)
}

$bindings = for ($index = 0; $index -lt $expectedNodes.Count; $index++) {
    [ordered]@{
        sidecarNode = $index
        sourceNode = $index
        name = $expectedNodes[$index]
    }
}

foreach ($kind in @('wild', 'sile')) {
    $entries = foreach ($name in $requiredAnimations) {
        [ordered]@{
            name = $name
            file = "animations/$name.animation.gltf.json"
            selfContained = $false
            nodeBindings = @($bindings)
        }
    }
    $manifest = [ordered]@{
        schema = 'haikalat.gltf-animation-library/1'
        generatedBy = 'Project Crystal Fracture player animation importer'
        gltfVersion = '2.0'
        model = "player_$kind.gltf"
        nodeBinding = 'name'
        sharedRig = $true
        animationData = 'animations/player_animation.bin'
        animations = @($entries)
    }
    $manifestPath = Join-Path $Destination "player_$kind.animation-library.json"
    $manifestJson = $manifest | ConvertTo-Json -Depth 100 -Compress
    [IO.File]::WriteAllText($manifestPath, $manifestJson + [Environment]::NewLine, $utf8)
}
