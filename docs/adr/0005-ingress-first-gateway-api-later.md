# ADR 0005 — Use Ingress first, Gateway API later

## Status

Proposed

## Context

Open Ecosystem OS will eventually run as a self-hosted multi-service platform. The Kubernetes deployment needs a clear entry-point model for public and protected HTTP traffic.

Expected routes include:

- public web application
- backend API
- documentation or product pages
- optional protected Grafana route
- future developer/admin routes

Kubernetes offers two relevant models:

- **Ingress**, the older and widely supported HTTP routing API.
- **Gateway API**, the newer and more expressive networking API.

Ingress is stable and simple for the first deployment, but the Kubernetes documentation describes Ingress as frozen and recommends Gateway API for newer use cases. Gateway API is also explicitly described as the successor to Ingress and provides a more role-oriented, portable, expressive, and extensible model.

For this project, the first Kubernetes goal is not advanced traffic management. The first goal is to get a clear, reproducible, self-hosted deployment working without unnecessary networking complexity.

## Decision

Use **Kubernetes Ingress for the MVP Kubernetes deployment**.

Document **Gateway API as the preferred target model** for the mature Kubernetes deployment.

The initial Kubernetes manifests may include:

- one Ingress for the public web route
- one Ingress path or hostname for the API
- optional protected route for Grafana only when authentication/network restrictions are in place

Gateway API should be introduced later when one or more of these conditions are true:

- the deployment needs clearer platform/application ownership boundaries
- multiple hostnames and protected routes become harder to manage with Ingress
- traffic weighting, header matching, canary routing, or richer routing policies are needed
- the selected cluster/distribution has a stable Gateway API implementation available
- the project is ready to document a more production-oriented networking model

## Consequences

### Positive

- Lower initial Kubernetes complexity.
- Easier local and self-hosted setup.
- Better compatibility with common tutorials, local clusters, and simple home-server deployments.
- Faster path to a working deployment.
- Clear migration path to Gateway API when the infrastructure becomes more mature.
- Avoids blocking the MVP on Gateway API controller/CRD setup.

### Negative

- Ingress may require controller-specific annotations for advanced features.
- Future migration will require converting Ingress resources to Gateway/HTTPRoute resources.
- Ingress does not model infrastructure/platform ownership as clearly as Gateway API.
- Some routing needs may become less portable if they depend heavily on controller-specific annotations.

## Alternatives considered

### Start directly with Gateway API

Rejected for MVP.

Gateway API is the better long-term model, but it requires installing CRDs or choosing an implementation that supports them. It also introduces GatewayClass, Gateway, HTTPRoute, and related concepts earlier than needed.

### Use only Docker Compose and skip Kubernetes networking

Rejected as a complete strategy.

Docker Compose is enough for local development, but Kubernetes is part of the self-hosting and infrastructure story of the project. The Kubernetes deployment still needs an HTTP entry-point model.

### Use a service mesh immediately

Rejected.

A service mesh would add unnecessary operational complexity for the current scope.

## Implementation notes

For MVP:

```txt
public traffic
  -> Ingress controller
  -> web service
  -> api service
```

Later target model:

```txt
GatewayClass
  -> Gateway
      -> HTTPRoute: web
      -> HTTPRoute: api
      -> HTTPRoute: docs
      -> HTTPRoute: protected observability route
```

The repo may eventually contain both profiles:

```txt
infra/k8s/base/ingress.yaml
infra/k8s/gateway/gateway.yaml
infra/k8s/gateway/httproutes.yaml
```

Gateway API manifests should not replace the MVP Ingress manifests until they are tested in the target local/self-hosted Kubernetes environment.

## References

- Kubernetes Ingress documentation: https://kubernetes.io/docs/concepts/services-networking/ingress/
- Kubernetes Gateway API documentation: https://kubernetes.io/docs/concepts/services-networking/gateway/
