import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { GEST_SERVICE_BASE_URL } from './gest-service.config';

// ---------------------------------------------------------------------------
// DetectionContractService — SPEC-06 §4 / GEST-FE-009
//
// negotiate(): fetches the frozen contract descriptor from the backend via
// GET /gest-service/detection-contract (guard: gest.detection-contract.read)
// and memoizes the result for the lifetime of the app.
//
// Backend response shape (mirrored from DetectionContractService.java ContractDescriptorV1):
//   { version: "v1.0.0", requiredFields: [...], forbiddenFields: [...] }
//
// What is NOT done here (explicitly deferred):
// - Per-stream compatibility validation: checking that ENGINE_FEATURE matches
//   contract.requiredFields before openStream() runs.
//   Reason: the stream-open path lives in EdgeDetectionBrowserAdapter (ADR-007)
//   and requires a coordinated change with that adapter's init sequence.
//   Tracked in GEST-FE-009 as remaining scope after P1.
// ---------------------------------------------------------------------------

export interface DetectionContractDescriptor {
  readonly version: string;
  readonly requiredFields: readonly string[];
  readonly forbiddenFields: readonly string[];
}

@Injectable({ providedIn: 'root' })
export class DetectionContractService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(GEST_SERVICE_BASE_URL);

  private cachedDescriptor: DetectionContractDescriptor | null = null;

  /**
   * Fetches the detection contract descriptor from gest-service and caches it.
   *
   * Fail-safe: on 4xx or network error, returns a safe fallback matching the
   * known v1.0.0 frozen contract so bootstrap is never blocked.
   *
   * Second call returns the cached result without an HTTP round-trip.
   */
  async negotiate(): Promise<DetectionContractDescriptor> {
    if (this.cachedDescriptor !== null) {
      return this.cachedDescriptor;
    }

    try {
      const descriptor = await firstValueFrom(
        this.http.get<DetectionContractDescriptor>(
          `${this.baseUrl}/gest-service/detection-contract`,
        ),
      );
      this.cachedDescriptor = descriptor;
      return descriptor;
    } catch {
      // gest-service unavailable at module init: use the known v1.0.0 fallback so
      // bootstrap is not blocked. The cache is still populated so repeated calls are cheap.
      const fallback: DetectionContractDescriptor = {
        version: 'unknown',
        requiredFields: ['gestureId', 'confidence', 'windowMs'],
        forbiddenFields: ['video', 'image', 'landmark', 'base64', 'pixels', 'frame'],
      };
      this.cachedDescriptor = fallback;
      return fallback;
    }
  }
}
