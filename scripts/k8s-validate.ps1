param(
  [string] $KubeconformImage = "ghcr.io/yannh/kubeconform:latest"
)

$ErrorActionPreference = "Stop"

$targets = @(
  "infra/k8s/base",
  "infra/k8s/overlays/dev",
  "infra/k8s/overlays/prod"
)

$kubeconform = Get-Command kubeconform -ErrorAction SilentlyContinue
$docker = Get-Command docker -ErrorAction SilentlyContinue

foreach ($target in $targets) {
  if ($kubeconform) {
    Write-Host "Validating $target with local kubeconform"
    kubectl kustomize $target | kubeconform -strict -summary
  } elseif ($docker) {
    Write-Host "Validating $target with Dockerized kubeconform"
    kubectl kustomize $target | docker run --rm -i $KubeconformImage -strict -summary
  } else {
    Write-Host "kubeconform and docker not installed; rendering $target only."
    kubectl kustomize $target | Out-Null
  }
}
