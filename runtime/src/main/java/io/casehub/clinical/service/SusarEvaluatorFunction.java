package io.casehub.clinical.service;

import io.casehub.worker.api.WorkerResult;
import java.util.Map;
import java.util.function.Function;

/**
 * Named CDI interface for the SUSAR criteria evaluator worker function.
 *
 * <p>Extends {@code Function<Map<String,Object>,WorkerResult>} as a named interface to
 * prevent CDI generic-erasure ambiguity and establish a clean displacement contract:
 * a future ML-based evaluator implements this interface as {@code @ApplicationScoped}
 * (without {@code @DefaultBean}) and displaces the default automatically.
 */
public interface SusarEvaluatorFunction extends Function<Map<String, Object>, WorkerResult<Map<String, Object>>> {}
