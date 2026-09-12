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

"""Tests the guarded prohibited-model source lifecycle."""

from __future__ import annotations

import csv
import hashlib
import importlib.util
import os
import re
import signal
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "prohibited-model-lifecycle.py"
SPEC = importlib.util.spec_from_file_location("prohibited_model_lifecycle", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ProhibitedModelLifecycleTest(unittest.TestCase):
    """Covers isolation, exact hashes, staging, cleanup, and stale recovery."""

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.previous = Path.cwd()
        os.chdir(self.root)
        Path("models").mkdir()
        Path("models/model-quarantine.properties").write_text(
            "aa-default=https://github.com/unimorph/aaa|No evidence\n"
            "bb-default=https://github.com/unimorph/bbb|No evidence\n",
            encoding="utf-8",
        )
        Path("models/model-projects.properties").write_text("cc-default=standalone\n", encoding="utf-8")
        Path("models/alternative-model-projects.properties").write_text("dd-filtered=filtered\n", encoding="utf-8")
        Path(".gitignore").write_text(
            MODULE.IGNORE_HEADER + "\n"
            "/models.prohibited/\n/models/aa-default/\n/models/bb-default/\n",
            encoding="utf-8",
        )
        lines = []
        for model_id in ("aa-default", "bb-default"):
            path = Path("models.prohibited") / model_id / "src/modelInput/stemmer.gz"
            path.parent.mkdir(parents=True)
            content = (model_id + "\n").encode()
            path.write_bytes(content)
            lines.append(f"{hashlib.sha256(content).hexdigest()}  {model_id}/src/modelInput/stemmer.gz")
        Path("models/prohibited-model-sources.sha256").write_text("\n".join(lines) + "\n", encoding="utf-8")
        self.untracked = mock.patch.object(MODULE, "verify_untracked", return_value=None)
        self.untracked.start()

    def tearDown(self) -> None:
        self.untracked.stop()
        os.chdir(self.previous)
        self.temporary.cleanup()

    def test_stage_restore_and_stale_recovery_preserve_bytes(self) -> None:
        MODULE.verify_normal()
        MODULE.stage()
        MODULE.verify_staged()
        self.assertTrue(Path("models/aa-default/src/modelInput/stemmer.gz").is_file())
        MODULE.recover()
        MODULE.verify_normal()
        self.assertEqual(b"aa-default\n", Path("models.prohibited/aa-default/src/modelInput/stemmer.gz").read_bytes())

    def test_partial_stage_is_restored_from_published_journal(self) -> None:
        MODULE.write_marker()
        Path("models.prohibited/aa-default").rename(Path("models/aa-default"))

        MODULE.recover()

        MODULE.verify_normal()
        self.assertFalse(MODULE.MARKER.exists())

    def test_unpublished_temporary_journal_is_recovered_before_moves(self) -> None:
        MODULE.MARKER_TEMP.parent.mkdir(parents=True)
        MODULE.MARKER_TEMP.write_text("incomplete", encoding="utf-8")

        MODULE.recover()

        MODULE.verify_normal()
        self.assertFalse(MODULE.MARKER_TEMP.exists())

    def test_non_os_staging_failure_restores_partial_move(self) -> None:
        original_rename = Path.rename

        def failing_rename(source: Path, destination: Path) -> Path:
            if source == Path("models.prohibited/bb-default"):
                raise RuntimeError("injected staging failure")
            return original_rename(source, destination)

        with mock.patch.object(Path, "rename", new=failing_rename):
            with self.assertRaisesRegex(RuntimeError, "injected staging failure"):
                MODULE.stage()

        MODULE.verify_normal()

    def test_hup_int_and_term_restore_partial_stage(self) -> None:
        original_rename = Path.rename
        for signum in (signal.SIGHUP, signal.SIGINT, signal.SIGTERM):
            with self.subTest(signum=signum):
                def interrupted_rename(source: Path, destination: Path) -> Path:
                    if source == Path("models.prohibited/bb-default"):
                        raise MODULE.LifecycleSignal(signum)
                    return original_rename(source, destination)

                with mock.patch.object(Path, "rename", new=interrupted_rename):
                    with self.assertRaises(MODULE.LifecycleSignal):
                        MODULE.stage()

                MODULE.verify_normal()
    def test_mutation_and_incomplete_store_are_rejected(self) -> None:
        Path("models.prohibited/aa-default/src/modelInput/stemmer.gz").write_bytes(b"changed")
        with self.assertRaisesRegex(MODULE.LifecycleError, "SHA-256 mismatch"):
            MODULE.verify_normal()
        Path("models.prohibited/aa-default/src/modelInput/stemmer.gz").write_bytes(b"aa-default\n")
        Path("models.prohibited/bb-default").rename(Path("missing-bb"))
        with self.assertRaisesRegex(MODULE.LifecycleError, "incomplete"):
            MODULE.verify_normal()

    def test_entirely_absent_private_store_is_valid(self) -> None:
        Path("models.prohibited/aa-default").rename(Path("missing-aa"))
        Path("models.prohibited/bb-default").rename(Path("missing-bb"))
        MODULE.verify_normal()


class RepositoryProhibitedModelGuardTest(unittest.TestCase):
    """Binds the lifecycle to settings and the publication-task deny policy."""

    def test_gradle_guard_is_exact_and_release_tasks_are_not_permitted(self) -> None:
        settings = Path("settings.gradle").read_text(encoding="utf-8")
        build = Path("build.gradle").read_text(encoding="utf-8")
        self.assertIn("radixorInternalProhibitedBenchmark", settings)
        self.assertIn("Prohibited model directory must not remain under models/", settings)
        permitted = re.search(
            r"final Set<String> permittedTasks = \[(.*?)\] as Set<String>",
            build,
            re.DOTALL,
        )
        self.assertIsNotNone(permitted)
        assert permitted is not None
        for forbidden in ("publish", "sign", "release", "catalog", "package", "sourcesJar"):
            self.assertNotIn(forbidden, permitted.group(1))

    def test_repository_manifests_have_one_closed_non_public_cohort(self) -> None:
        quarantine = MODULE.read_properties(Path("models/model-quarantine.properties"))
        active = MODULE.read_properties(Path("models/model-projects.properties"))
        alternative = MODULE.read_properties(Path("models/alternative-model-projects.properties"))
        self.assertEqual(23, len(quarantine))
        self.assertFalse(set(quarantine) & (set(active) | set(alternative)))
        self.assertEqual(
            ("/models.prohibited/", *(f"/models/{model_id}/" for model_id in sorted(quarantine))),
            MODULE.expected_ignore_rules(),
        )
        with Path("models/prohibited-model-documentation.csv").open(encoding="utf-8", newline="") as stream:
            metadata = list(csv.DictReader(stream))
        self.assertEqual(set(quarantine), {row["Model ID"] for row in metadata})
        for model_id in quarantine:
            self.assertFalse((Path("models") / model_id).exists())

    def test_runner_uses_only_guarded_manifest_ids_and_canonical_protocol(self) -> None:
        runner = Path("tools/run-prohibited-model-benchmarks.sh").read_text(encoding="utf-8")
        self.assertIn("python3 tools/prohibited-model-lifecycle.py stage", runner)
        self.assertGreaterEqual(
            runner.count("python3 tools/prohibited-model-lifecycle.py recover"), 2
        )
        self.assertIn("trap 'exit 129' HUP", runner)
        self.assertIn("trap 'exit 130' INT", runner)
        self.assertIn("trap 'exit 143' TERM", runner)
        self.assertIn("manual recovery is required", runner)
        self.assertIn('model_ids="$(tail -n +2 "${manifest}" | cut -f1 | paste -sd, -)"', runner)
        self.assertIn('-p "modelId=${model_ids}" -f 3 -wi 3 -i 5 -w 1s -r 1s -t 1', runner)
        self.assertIn("-Dradixor.prohibited.manifest=${project_root}/${manifest}", runner)
        self.assertNotIn("model-projects.properties", runner)
        self.assertIn("ProhibitedSnowballLanguageCatalogApplication", runner)
        self.assertIn("--comparator-catalog \"${comparator_catalog}\"", runner)
        self.assertNotIn("SNOWBALL_BASQUE_DIRECT", runner)
        self.assertNotIn("cy-gb-default", runner)
        self.assertNotIn("sl-si-default", runner)

    def test_active_prohibited_snapshot_pointer_is_a_mandatory_check_input(self) -> None:
        build = Path("build.gradle").read_text(encoding="utf-8")
        task = re.search(
            r"tasks\.register\('verifyPublishedProhibitedBenchmarkDocumentation'.*?\n}\n",
            build,
            re.DOTALL,
        )
        self.assertIsNotNone(task)
        assert task is not None
        self.assertNotIn("onlyIf", task.group(0))
        self.assertIn("Missing active prohibited benchmark snapshot pointer", task.group(0))

    def test_user_documentation_distinguishes_model_categories(self) -> None:
        lifecycle = Path("docs/stemmer-models.md").read_text(encoding="utf-8")
        java_customization = Path("docs/programmatic-extending-and-persistence.md").read_text(encoding="utf-8")
        self.assertIn("## Model categories and lifecycle", lifecycle)
        for category in ("Default", "Standalone default", "Optional", "Filtered alternative", "Prohibited / quarantine"):
            self.assertIn(category, lifecycle)
        self.assertNotIn("Extended default", lifecycle)
        self.assertIn("`extended` is therefore a package membership, never", lifecycle)
        self.assertIn("### Concrete before-and-after example", java_customization)
        for operation in ("putDominant", "putIfAbsent", "remove(key, value)", "set"):
            self.assertIn(operation, java_customization)

    def test_quality_documentation_uses_current_default_count(self) -> None:
        documentation = Path("docs/stemming-quality.md").read_text(encoding="utf-8")
        active = MODULE.read_properties(Path("models/model-projects.properties"))
        default_count = sum(role != "optional" for role in active.values())
        self.assertEqual(143, default_count)
        self.assertIn(f"All {default_count} current values", documentation)
        self.assertIn(f"all {default_count} current default models", documentation)
        self.assertNotIn("all 20 current", documentation)


if __name__ == "__main__":
    unittest.main()
