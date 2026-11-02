#!/usr/bin/env python3
"""Static release-policy regression checks; not a substitute for a live publish test."""
from pathlib import Path
import unittest


WORKFLOW = Path(__file__).resolve().parents[1] / ".github/workflows/maven.yml"


class ImmutableReleaseWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.publish = cls.workflow.split("- name: Publish raw JAR release asset", 1)[1]

    def test_default_branch_push_never_publishes(self):
        self.assertIn("github.event_name == 'workflow_dispatch' &&", self.publish)
        self.assertIn("github.ref_name == github.event.repository.default_branch", self.publish)
        self.assertIn("github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')", self.publish)
        self.assertNotIn("github.ref_name == github.event.repository.default_branch\n", self.publish.split("env:", 1)[0])

    def test_existing_release_is_read_only(self):
        self.assertIn('gh release view "$TAG"', self.publish)
        self.assertIn('gh release download "$TAG"', self.publish)
        self.assertIn('test -s "$EXISTING"', self.publish)
        self.assertIn('cmp -s "$JAR" "$EXISTING"', self.publish)
        self.assertNotIn("--clobber", self.publish)
        self.assertNotIn("gh release upload", self.publish)
        self.assertIn("refusing overwrite", self.publish)

    def test_only_missing_release_can_be_created(self):
        self.assertIn('if gh release view "$TAG"', self.publish)
        self.assertIn('else\n            gh release create "$TAG"', self.publish)
        self.assertIn('"$TAG_SOURCE" != "$GITHUB_SHA"', self.publish)


    def test_shell_continuations_have_exactly_one_backslash(self):
        # A doubled trailing slash becomes a literal argument, not a newline escape.
        for line in self.publish.splitlines():
            if line.endswith("\\"):
                self.assertFalse(line.endswith("\\\\"), "Invalid doubled shell line continuation")


if __name__ == "__main__":
    unittest.main()
