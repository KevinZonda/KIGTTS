param(
  [string]$WorkDir = "",
  [string]$OutputDir = "",
  [switch]$Force,
  [switch]$SkipPiper,
  [switch]$SkipVoxCpm
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$trainerRoot = Resolve-Path (Join-Path $scriptDir "..")
$repoRoot = Resolve-Path (Join-Path $trainerRoot "..")

if ([string]::IsNullOrWhiteSpace($WorkDir)) {
  $WorkDir = Join-Path $repoRoot "runtime-build"
}
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
  $OutputDir = Join-Path $trainerRoot "dist-runtime"
}

$workRoot = New-Item -ItemType Directory -Force -Path $WorkDir
$env:PIP_CACHE_DIR = Join-Path $workRoot.FullName "pip-cache"
$env:TEMP = Join-Path $workRoot.FullName "tmp"
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force -Path $env:PIP_CACHE_DIR | Out-Null
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null

$piperEnv = Join-Path $workRoot.FullName "piper_env_cuda128"
$voxcpmEnv = Join-Path $workRoot.FullName "voxcpm_env_cuda128"
$mambaRoot = Join-Path $workRoot.FullName "mamba-root"
$piperWheels = Join-Path $repoRoot "pc_trainer\piper_wheels"
$piperTrainWheel = Join-Path $piperWheels "piper_train-1.0.0-py3-none-any.whl"
$piperRequirements = Join-Path $trainerRoot "backend\engine\piper_cuda_requirements.txt"
$micromamba = Join-Path $trainerRoot "build\micromamba\micromamba.exe"
$packScript = Join-Path $scriptDir "pack_runtime_archives.ps1"

if (!(Test-Path -LiteralPath $micromamba)) {
  throw "micromamba.exe not found: $micromamba"
}
if (!(Test-Path -LiteralPath $piperTrainWheel)) {
  throw "piper_train wheel not found: $piperTrainWheel"
}

function Invoke-Step {
  param(
    [string]$Title,
    [scriptblock]$Body
  )
  Write-Host ""
  Write-Host "== $Title =="
  & $Body
}

function Invoke-CommandChecked {
  param(
    [string]$FilePath,
    [string[]]$Arguments,
    [string]$WorkingDirectory = $repoRoot
  )
  Write-Host ("> " + $FilePath + " " + ($Arguments -join " "))
  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed ($LASTEXITCODE): $FilePath $($Arguments -join ' ')"
  }
}

function New-PythonEnv {
  param(
    [string]$EnvPath
  )
  if ((Test-Path -LiteralPath $EnvPath) -and $Force) {
    Remove-Item -LiteralPath $EnvPath -Recurse -Force
  }
  if (Test-Path -LiteralPath (Join-Path $EnvPath "python.exe")) {
    Write-Host "Environment already exists: $EnvPath"
    return
  }
  $env:MAMBA_ROOT_PREFIX = $mambaRoot
  Invoke-CommandChecked $micromamba @(
    "create",
    "-y",
    "--no-rc",
    "--override-channels",
    "-r",
    $mambaRoot,
    "-p",
    $EnvPath,
    "python=3.10",
    "pip",
    "-c",
    "https://conda.anaconda.org/conda-forge"
  )
}

function Install-PipToolchain {
  param(
    [string]$Python
  )
  Invoke-CommandChecked $Python @("-m", "pip", "install", "--disable-pip-version-check", "--no-input", "pip==24.0", "setuptools==80.9.0", "wheel")
}

function Install-TorchCu128 {
  param(
    [string]$Python
  )
  Invoke-CommandChecked $Python @(
    "-m",
    "pip",
    "install",
    "--disable-pip-version-check",
    "--no-input",
    "--prefer-binary",
    "--index-url",
    "https://download.pytorch.org/whl/cu128",
    "--extra-index-url",
    "https://pypi.org/simple",
    "torch==2.7.1+cu128",
    "torchvision==0.22.1+cu128",
    "torchaudio==2.7.1+cu128"
  )
}

function Install-PiperRuntime {
  if ($SkipPiper) {
    return
  }
  Invoke-Step "Create Piper CUDA 12.8 env" {
    New-PythonEnv $piperEnv
  }
  $python = Join-Path $piperEnv "python.exe"
  Invoke-Step "Install Piper pip toolchain" {
    Install-PipToolchain $python
  }
  Invoke-Step "Install Piper PyTorch cu128" {
    Install-TorchCu128 $python
  }
  Invoke-Step "Install Piper runtime dependencies" {
    Invoke-CommandChecked $python @(
      "-m",
      "pip",
      "install",
      "--disable-pip-version-check",
      "--no-input",
      "--prefer-binary",
      "--find-links",
      $piperWheels,
      "--index-url",
      "https://mirrors.aliyun.com/pypi/simple",
      "--extra-index-url",
      "https://pypi.org/simple",
      "-r",
      $piperRequirements
    )
    Invoke-CommandChecked $python @(
      "-m",
      "pip",
      "install",
      "--disable-pip-version-check",
      "--no-input",
      "--prefer-binary",
      "--index-url",
      "https://mirrors.aliyun.com/pypi/simple",
      "--extra-index-url",
      "https://pypi.org/simple",
      "cython>=0.29,<1"
    )
    Invoke-CommandChecked $python @("-m", "pip", "install", "--disable-pip-version-check", "--no-input", "--no-deps", $piperTrainWheel)
  }
  Invoke-Step "Probe Piper CUDA 12.8 env" {
    Invoke-CommandChecked $python @(
      "-c",
      "import json, torch, torchaudio, pytorch_lightning, piper_train; print(json.dumps({'torch': torch.__version__, 'cuda': getattr(torch.version, 'cuda', None), 'cuda_available': torch.cuda.is_available(), 'torchaudio': torchaudio.__version__, 'lightning': pytorch_lightning.__version__, 'piper_train': getattr(piper_train, '__file__', '')}, ensure_ascii=False))"
    )
  }
}

function Install-VoxCpmRuntime {
  if ($SkipVoxCpm) {
    return
  }
  Invoke-Step "Create VoxCPM2 CUDA 12.8 env" {
    New-PythonEnv $voxcpmEnv
  }
  $python = Join-Path $voxcpmEnv "python.exe"
  Invoke-Step "Install VoxCPM2 pip toolchain" {
    Install-PipToolchain $python
  }
  Invoke-Step "Install VoxCPM2 PyTorch cu128" {
    Install-TorchCu128 $python
  }
  Invoke-Step "Install VoxCPM2 runtime dependencies" {
    Invoke-CommandChecked $python @(
      "-m",
      "pip",
      "install",
      "--disable-pip-version-check",
      "--no-input",
      "--prefer-binary",
      "--index-url",
      "https://mirrors.aliyun.com/pypi/simple",
      "--extra-index-url",
      "https://pypi.org/simple",
      "voxcpm==2.0.2",
      "modelscope",
      "soundfile"
    )
  }
  Invoke-Step "Probe VoxCPM2 CUDA 12.8 env" {
    Invoke-CommandChecked $python @(
      "-c",
      "import importlib.metadata as md, json, torch, soundfile; from voxcpm import VoxCPM; print(json.dumps({'torch': torch.__version__, 'cuda': getattr(torch.version, 'cuda', None), 'cuda_available': torch.cuda.is_available(), 'voxcpm': md.version('voxcpm'), 'modelscope': md.version('modelscope')}, ensure_ascii=False))"
    )
  }
}

Install-PiperRuntime
Install-VoxCpmRuntime

Invoke-Step "Pack CUDA 12.8 runtime archives" {
  & $packScript `
    -OutputDir $OutputDir `
    -PiperEnv (Join-Path $workRoot.FullName "missing-piper-env") `
    -PiperCudaEnv (Join-Path $workRoot.FullName "missing-piper-cuda-env") `
    -PiperCuda128Env $piperEnv `
    -VoxCpmEnv (Join-Path $workRoot.FullName "missing-voxcpm-env") `
    -VoxCpmCuda128Env $voxcpmEnv
  if ($LASTEXITCODE -ne 0) {
    throw "Runtime archive packing failed."
  }
}

Write-Host ""
Write-Host "CUDA 12.8 runtime archives are ready:"
Get-ChildItem -LiteralPath $OutputDir -Filter "*cuda128.7z" | Select-Object FullName, Length
