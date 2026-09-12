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

"""Regression tests for portable exact model-coverage validation."""

from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = PROJECT_ROOT / "tools" / "validate-speed-model-coverage.sh"
RESUME_RUNNER = PROJECT_ROOT / "tools" / "resume-published-speed-coverage.sh"
CLASSPATH_MANIFEST_WRITER = (
    PROJECT_ROOT / "tools" / "write-classpath-content-manifest.sh"
)
CANONICAL_RUNNER = PROJECT_ROOT / "tools" / "run-published-speed-benchmarks.sh"


class SpeedModelCoverageTest(unittest.TestCase):
    """Executes the shell validator against representative JMH CSV fixtures."""

    def setUp(self) -> None:
        """Create a complete 167-model topology and matching speed report."""

        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        fixture_directory = Path(self.temporary_directory.name)
        self.topology = fixture_directory / "model-projects.properties"
        self.speed_report = fixture_directory / "speed.csv"
        self.model_ids = [f"aa-{number:03d}-default" for number in range(167)]
        self.topology.write_text(
            "".join(f"{model_id}=standalone\n" for model_id in self.model_ids),
            encoding="utf-8",
        )
        self.write_speed_report(self.model_ids)

    def write_speed_report(self, model_ids: list[str]) -> None:
        """Write a minimal JMH CSV with modelId intentionally not first."""

        rows = [
            '"Benchmark","Mode","Threads","Samples","Score","Unit","Param: modelId"\r\n'
        ]
        rows.extend(
            f'"RadixorModelStemmerBenchmark.radixor","avgt",1,15,'
            f'1000.0,"ns/op","{model_id}"\r\n'
            for model_id in model_ids
        )
        self.speed_report.write_text("".join(rows), encoding="utf-8")

    def run_validator(self) -> subprocess.CompletedProcess[str]:
        """Run the same shell entry point used by the canonical benchmark."""

        return subprocess.run(
            ["bash", str(VALIDATOR), str(self.speed_report), str(self.topology)],
            cwd=PROJECT_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )

    def test_accepts_exact_167_model_coverage(self) -> None:
        """The portable field loop extracts every parameterized model ID."""

        result = self.run_validator()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Validated exact speed coverage", result.stdout)

    def test_rejects_missing_model(self) -> None:
        """A successful JMH process may not silently omit a topology model."""

        self.write_speed_report(self.model_ids[:-1])

        result = self.run_validator()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("does not cover exactly", result.stderr)

    def test_resume_runner_cannot_repeat_main_suite(self) -> None:
        """The recovery entry point runs only coverage with the canonical protocol."""

        source = RESUME_RUNNER.read_text(encoding="utf-8")

        self.assertNotIn("comparison_include=", source)
        self.assertNotIn("\nsleep ", source)
        self.assertEqual(1, source.count("org.openjdk.jmh.Main"))
        for argument in ("-f 3", "-wi 3", "-i 5", "-w 1s", "-r 1s", "-t 1"):
            self.assertIn(argument, source)

    def test_canonical_runner_records_recovery_checksums(self) -> None:
        """A new canonical run pins its full classpath and validated main CSV."""

        source = CANONICAL_RUNNER.read_text(encoding="utf-8")

        self.assertIn("write-classpath-content-manifest.sh", source)
        validation_offset = source.index("validate-speed-model-coverage.sh")
        checksum_offset = source.index("Main speed report SHA-256")
        coverage_offset = source.index(
            '"${coverage_include}" "${common_arguments[@]}"', checksum_offset
        )
        self.assertLess(validation_offset, checksum_offset)
        self.assertLess(checksum_offset, coverage_offset)


class PublishedSpeedRecoveryTest(unittest.TestCase):
    """Runs retrospective pinning and coverage recovery in an isolated project."""

    REPORT_DATE = "2026-09-10"

    def setUp(self) -> None:
        """Build a complete fake run with two independently mutable artifacts."""

        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.project_root = Path(self.temporary_directory.name)
        tools_directory = self.project_root / "tools"
        reports_directory = self.project_root / "build" / "reports" / "jmh"
        runtime_directory = self.project_root / "runtime"
        models_directory = self.project_root / "models"
        stub_directory = self.project_root / "stub-bin"
        for directory in (
            tools_directory,
            reports_directory,
            runtime_directory,
            models_directory,
            stub_directory,
        ):
            directory.mkdir(parents=True, exist_ok=True)

        for source in (VALIDATOR, RESUME_RUNNER, CLASSPATH_MANIFEST_WRITER):
            shutil.copy2(source, tools_directory / source.name)

        self.main_artifact = runtime_directory / "main.jar"
        self.dependency_artifact = runtime_directory / "dependency.jar"
        self.classes_directory = runtime_directory / "classes"
        self.missing_artifact = runtime_directory / "missing-resources"
        self.classes_directory.mkdir()
        (self.classes_directory / "Benchmark.class").write_bytes(b"class-v1")
        self.main_artifact.write_bytes(b"main-v1")
        self.dependency_artifact.write_bytes(b"dependency-v1")

        self.classpath_file = reports_directory / "jmh-runtime-classpath.txt"
        self.classpath_file.write_text(
            ":".join(
                str(path)
                for path in (
                    self.main_artifact,
                    self.dependency_artifact,
                    self.classes_directory,
                    self.missing_artifact,
                )
            )
            + "\n",
            encoding="utf-8",
        )
        topology = models_directory / "model-projects.properties"
        self.model_ids = [f"aa-{number:03d}-default" for number in range(167)]
        topology.write_text(
            "".join(f"{model_id}=standalone\n" for model_id in self.model_ids),
            encoding="utf-8",
        )
        self.main_csv = reports_directory / f"stemmer-speed-{self.REPORT_DATE}.csv"
        self.main_csv.write_bytes(self.speed_report_bytes())
        self.original_main_csv = self.main_csv.read_bytes()
        self.environment_file = (
            reports_directory / f"performance-environment-{self.REPORT_DATE}.txt"
        )
        self.environment_file.write_text(
            "Benchmark start: historical\n"
            f"JMH runtime classpath SHA-256: {self.digest(self.classpath_file)}\n"
            f"JMH executable JAR SHA-256: {self.digest(self.main_artifact)}\n",
            encoding="utf-8",
        )

        self.java_log = self.project_root / "java-arguments.bin"
        java_stub = stub_directory / "java"
        java_stub.write_text(
            "#!/usr/bin/env bash\n"
            "set -euo pipefail\n"
            "printf '%s\\0' \"$@\" > \"${JAVA_ARGUMENT_LOG}\"\n"
            "previous=''\n"
            "for argument in \"$@\"; do\n"
            "    if [[ \"${previous}\" == '-rff' || \"${previous}\" == '-o' ]]; then\n"
            "        : > \"${argument}\"\n"
            "    fi\n"
            "    previous=\"${argument}\"\n"
            "done\n",
            encoding="utf-8",
        )
        java_stub.chmod(0o755)
        sensors_stub = stub_directory / "sensors"
        sensors_stub.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
        sensors_stub.chmod(0o755)
        self.environment = os.environ.copy()
        self.environment["PATH"] = (
            f"{stub_directory}{os.pathsep}{self.environment['PATH']}"
        )
        self.environment["JAVA_ARGUMENT_LOG"] = str(self.java_log)

        pin_result = self.run_recovery("--retrospective-pin-after-main")
        self.assertEqual(0, pin_result.returncode, pin_result.stderr)
        self.pinned_environment = self.environment_file.read_bytes()
        self.assertIn(b"Retrospective coverage-recovery pin", self.pinned_environment)

    @staticmethod
    def digest(path: Path) -> str:
        """Return the content digest recorded by the production scripts."""

        return hashlib.sha256(path.read_bytes()).hexdigest()

    def speed_report_bytes(self) -> bytes:
        """Create a complete CRLF JMH report for all topology models."""

        rows = [
            '"Benchmark","Mode","Threads","Samples","Score","Unit","Param: modelId"\r\n'
        ]
        rows.extend(
            f'"RadixorModelStemmerBenchmark.radixor","avgt",1,15,'
            f'1000.0,"ns/op","{model_id}"\r\n'
            for model_id in self.model_ids
        )
        return "".join(rows).encode("utf-8")

    def run_recovery(
        self, operation: str | None = None
    ) -> subprocess.CompletedProcess[str]:
        """Execute the copied recovery entry point with controlled commands."""

        arguments = [
            "bash",
            "tools/resume-published-speed-coverage.sh",
            self.REPORT_DATE,
        ]
        if operation is not None:
            arguments.append(operation)
        return subprocess.run(
            arguments,
            cwd=self.project_root,
            env=self.environment,
            check=False,
            capture_output=True,
            text=True,
        )

    def test_rejects_dependency_mutation_before_java_or_provenance_append(
        self,
    ) -> None:
        """Every effective classpath artifact is bound by the recovery pin."""

        self.dependency_artifact.write_bytes(b"dependency-v2")

        result = self.run_recovery()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("complete JMH runtime classpath differs", result.stderr)
        self.assertFalse(self.java_log.exists())
        self.assertEqual(self.pinned_environment, self.environment_file.read_bytes())
        self.assertEqual(self.original_main_csv, self.main_csv.read_bytes())

    def test_rejects_main_csv_mutation_before_java_or_provenance_append(self) -> None:
        """Recovery binds the validated main report byte for byte."""

        with self.main_csv.open("ab") as stream:
            stream.write(b"# non-model mutation\r\n")

        result = self.run_recovery()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("main speed report differs", result.stderr)
        self.assertFalse(self.java_log.exists())
        self.assertEqual(self.pinned_environment, self.environment_file.read_bytes())

    def test_runs_only_coverage_with_exact_protocol_and_appends_provenance(
        self,
    ) -> None:
        """A valid pin resumes the one remaining benchmark without touching main CSV."""

        result = self.run_recovery()

        self.assertEqual(0, result.returncode, result.stderr)
        arguments = [
            item.decode("utf-8")
            for item in self.java_log.read_bytes().split(b"\0")
            if item
        ]
        self.assertIn(
            "^org\\.egothor\\.stemmer\\.benchmark\\."
            "EnglishRadixorDictionaryCoverageBenchmark\\."
            "changedTokenStemmingSpeed$",
            arguments,
        )
        for flag, value in (
            ("-f", "3"),
            ("-wi", "3"),
            ("-i", "5"),
            ("-w", "1s"),
            ("-r", "1s"),
            ("-t", "1"),
        ):
            offset = arguments.index(flag)
            self.assertEqual(value, arguments[offset + 1])
        self.assertNotIn("RadixorModelStemmerBenchmark", " ".join(arguments))
        self.assertEqual(self.original_main_csv, self.main_csv.read_bytes())
        completed_environment = self.environment_file.read_bytes()
        self.assertTrue(completed_environment.startswith(self.pinned_environment))
        self.assertLess(
            completed_environment.index(b"Retrospective coverage-recovery pin"),
            completed_environment.index(b"Coverage-only resume start"),
        )
        self.assertIn(b"Validated main speed report SHA-256", completed_environment)
        self.assertIn(b"Benchmark end", completed_environment)


if __name__ == "__main__":
    unittest.main()
