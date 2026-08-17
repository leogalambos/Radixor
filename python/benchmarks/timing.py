###############################################################################
# Copyright (C) 2026, Leo Galambos
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice,
#    this list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors
#    may be used to endorse or promote products derived from this software
#    without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.
###############################################################################

"""Timing primitives shared by the Python stemming benchmarks.

The benchmark deliberately measures samples long enough to reduce sensitivity
on CPU boost transitions, scheduler jitter, timer granularity, and other
sub-millisecond effects.  Each reported point is based on the median of several
calibrated samples; the minimum is retained only as a diagnostic statistic.
"""

from __future__ import annotations

import math
import statistics
import time
from dataclasses import dataclass
from typing import Callable, Sequence

BatchFunction = Callable[[list[str]], object]


@dataclass(frozen=True)
class TimingResult:
    """Summary of calibrated benchmark samples for one measurement point."""

    passes_per_sample: int
    samples_ns: tuple[int, ...]
    median_ns: float
    minimum_ns: int
    maximum_ns: int
    mad_ns: float

    @property
    def relative_mad_percent(self) -> float:
        """Return median absolute deviation as a percentage of the median."""
        if self.median_ns <= 0.0:
            return 0.0
        return 100.0 * self.mad_ns / self.median_ns


def run_passes_ns(
    batch_fn: BatchFunction,
    batches: Sequence[list[str]],
    passes: int,
) -> int:
    """Run ``passes`` complete corpus traversals and return elapsed nanoseconds."""
    start = time.perf_counter_ns()
    for _ in range(passes):
        for batch in batches:
            batch_fn(batch)
    return time.perf_counter_ns() - start


def calibrate_passes(
    batch_fn: BatchFunction,
    batches: Sequence[list[str]],
    target_sample_ns: int,
    maximum_passes: int = 10_000_000,
) -> int:
    """Choose corpus passes that make one timed sample approximately target-sized.

    A short pilot is intentionally performed with the exact workload.  The
    resulting pass count is bounded to protect against accidental zero-work
    functions or implausibly small pilot measurements.
    """
    if target_sample_ns <= 0:
        raise ValueError("target_sample_ns must be positive")
    if maximum_passes <= 0:
        raise ValueError("maximum_passes must be positive")

    pilot_ns = run_passes_ns(batch_fn, batches, 1)
    if pilot_ns <= 0:
        return 1

    estimated = max(1, min(int(math.ceil(target_sample_ns / pilot_ns)), maximum_passes))

    # Refine once using a sample long enough to average out most timer noise.
    # This also accommodates engines whose cost does not scale perfectly from a
    # single corpus traversal because of CPU power-state transitions.
    refinement_ns = run_passes_ns(batch_fn, batches, estimated)
    if refinement_ns <= 0:
        return estimated
    refined = int(math.ceil(estimated * target_sample_ns / refinement_ns))
    return max(1, min(refined, maximum_passes))


def warm_up(
    batch_fn: BatchFunction,
    batches: Sequence[list[str]],
    calibrated_passes: int,
    minimum_passes: int,
    minimum_duration_ns: int,
) -> None:
    """Warm a workload for both a minimum pass count and a minimum duration."""
    if calibrated_passes <= 0:
        raise ValueError("calibrated_passes must be positive")
    if minimum_passes < 0:
        raise ValueError("minimum_passes must be non-negative")
    if minimum_duration_ns < 0:
        raise ValueError("minimum_duration_ns must be non-negative")

    start = time.perf_counter_ns()
    completed = 0
    while (
        completed < minimum_passes
        or time.perf_counter_ns() - start < minimum_duration_ns
    ):
        run_passes_ns(batch_fn, batches, calibrated_passes)
        completed += calibrated_passes


def measure(
    batch_fn: BatchFunction,
    batches: Sequence[list[str]],
    repeats: int,
    target_sample_ns: int,
    warmup_passes: int,
    warmup_duration_ns: int,
) -> TimingResult:
    """Calibrate, warm, and measure one benchmark point.

    The median is the primary estimator.  The minimum is preserved only for
    diagnostics and backward visibility into historical benchmark behavior.
    """
    if repeats <= 0:
        raise ValueError("repeats must be positive")

    passes = calibrate_passes(batch_fn, batches, target_sample_ns)
    warm_up(
        batch_fn,
        batches,
        calibrated_passes=passes,
        minimum_passes=warmup_passes,
        minimum_duration_ns=warmup_duration_ns,
    )
    samples = tuple(run_passes_ns(batch_fn, batches, passes) for _ in range(repeats))
    median_ns = statistics.median(samples)
    deviations = [abs(sample - median_ns) for sample in samples]
    mad_ns = statistics.median(deviations)
    return TimingResult(
        passes_per_sample=passes,
        samples_ns=samples,
        median_ns=median_ns,
        minimum_ns=min(samples),
        maximum_ns=max(samples),
        mad_ns=mad_ns,
    )
