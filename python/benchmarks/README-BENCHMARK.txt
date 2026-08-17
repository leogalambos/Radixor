Radixor CPU-scaling benchmark package
=====================================

Purpose
-------

This package replaces the short/minimum-based timing methodology with calibrated
long-running samples and median reporting. It also adds a Radixor-only diagnostic
benchmark that isolates native runtime stages.

Install location
----------------

Copy these files into the repository's python/benchmarks/ directory, replacing
corpus.py, engines.py, and run_benchmark.py and adding timing.py and
run_radixor_diagnostics.py.

Recommended system preparation (Linux)
--------------------------------------

    sudo cpupower frequency-set -g performance

If amd-pstate EPP is active and exposes the preference:

    for f in /sys/devices/system/cpu/cpu*/cpufreq/energy_performance_preference; do
        [ -e "$f" ] && echo performance | sudo tee "$f" >/dev/null
    done

Run the machine otherwise idle and use the same CPU affinity on both systems.
The scripts record the visible governor, EPP, amd-pstate status, and process CPU
affinity in JSON output.

Primary Radixor vs PyStemmer comparison
---------------------------------------

The canonical published run is:

    taskset -c 2 python python/benchmarks/run_benchmark.py \
        --all-languages \
        --engines radixor PyStemmer \
        --sizes 100 \
        --words 5000 \
        --repeats 3 \
        --sample-ms 250 \
        --warmup-ms 500 \
        --json build/reports/python-benchmarks/published-benchmark.json \
        --csv build/reports/python-benchmarks/published-benchmark.csv

The primary per_word_ns / throughput_words_per_s values now come from the
MEDIAN of calibrated samples. min_per_word_ns and min_throughput_words_per_s
are retained only to compare with the previous best/min methodology.

Radixor internal diagnostics
----------------------------

    taskset -c 2 python python/benchmarks/run_radixor_diagnostics.py \
        --language pl cs fi ru sv \
        --sizes 10 20 50 100 \
        --words 5000 \
        --repeats 9 \
        --sample-ms 250 \
        --warmup-ms 500 \
        --json radixor-diagnostics-$(hostname).json

The diagnostic stages are:

    marshal         _len_batch
    marshal+output  _echo_batch
    encode          _encode_batch
    encode+find     _encodefind_batch
    native-stem     _stem_lengths_batch
    full-output     stem_batch

The most useful cross-CPU comparison is the ratio of per_word_ns for each stage.
For example, if encode scales strongly but encode+find barely improves, trie
traversal is the likely limiting stage.

Provenance safeguards
---------------------

engines.py now records a SHA-256 digest of each native extension backing file.
The PyStemmer adapter also verifies that top-level `import Stemmer` resolves to
the installed PyStemmer distribution rather than Radixor's compatibility shim.

Timing methodology
------------------

Each measurement point:

1. Executes a pilot corpus traversal.
2. Calculates the number of corpus passes needed for approximately --sample-ms.
3. Refines that pass count using a longer calibration sample.
4. Warms for at least --warmup-ms and the requested minimum warmup passes.
5. Runs --repeats calibrated timed samples with Python cyclic GC disabled.
6. Reports the median as the primary measurement and MAD as stability metadata.

The corpus, cache-off configuration, Radixor lowercase=False setting, and batch
interfaces remain unchanged from the supplied benchmark.
