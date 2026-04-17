# Performance Baseline

## Target

- Throughput: ~200 req/s
- p95 latency: <= 500ms
- Error rate: < 1%
- Traffic mix: 70% `/profile`, 20% `/admin/users`, 10% `/login`

## Measurement Command

```bash
./load-test.sh
```

## Results

| Run | req/s | p95 | error rate | Notes |
|---|---:|---:|---:|---|
| Baseline before SRP/OAuth refactor | N/A | N/A | N/A | No comparable baseline captured in this workspace snapshot |
| Current mixed-load run (2026-04-17) | 49.34 | 12.3s | 0.00% | k6 reported high dropped iterations (`8645`), throughput/latency target not met |

## Verification Summary

- `./mvnw clean test`: PASS (`Tests run: 43, Failures: 0, Errors: 0`)
- `./mvnw -q -DskipTests package`: PASS
- `./load-test.sh`: Functional checks PASS, performance threshold FAIL (`p95=12.3s` vs target `<=500ms`)

## Observations

- Functional correctness under load is stable (`http_req_failed=0.00%`).
- Performance target is currently not met in this environment.
- k6 reached max VUs for all scenarios, indicating backend capacity below configured arrival rate target.

## Next Steps

1. Profile hot paths for `/profile`, `/admin/users`, and `/login` (DB query timings, JWT cost, Redis calls).
2. Remove/optimize non-critical synchronous work on request path.
3. Re-run `./load-test.sh` after each optimization batch and update this document.
