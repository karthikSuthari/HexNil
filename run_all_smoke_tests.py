"""Run all HexNil smoke tests (backend + AI)

Run:
  .\.venv\Scripts\python.exe .\run_all_smoke_tests.py

Options:
  --backend-only   Run only backend tests
  --ai-only        Run only AI tests
"""

from __future__ import annotations

import os
import sys
import subprocess
import argparse


def run_test(script: str) -> int:
    """Run a test script and return exit code."""
    py = sys.executable
    result = subprocess.run([py, script], cwd=os.path.dirname(__file__))
    return result.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description="Run HexNil smoke tests")
    parser.add_argument("--backend-only", action="store_true", help="Run only backend tests")
    parser.add_argument("--ai-only", action="store_true", help="Run only AI tests")
    args = parser.parse_args()

    print("=" * 70)
    print("HEXNIL SMOKE TEST SUITE")
    print("=" * 70)
    print()

    results = []

    if not args.ai_only:
        print("Running Backend Smoke Tests...")
        print("-" * 70)
        code = run_test("test_backend_smoke.py")
        results.append(("Backend", code))
        print()

    if not args.backend_only:
        print("Running AI Pipeline Smoke Tests...")
        print("-" * 70)
        code = run_test("test_ai_smoke.py")
        results.append(("AI Pipeline", code))
        print()

    # Summary
    print("=" * 70)
    print("OVERALL SUMMARY")
    print("=" * 70)
    
    all_passed = True
    for name, code in results:
        status = "PASSED" if code == 0 else "FAILED"
        symbol = "[OK]" if code == 0 else "[FAIL]"
        print(f"  {symbol} {name}: {status}")
        if code != 0:
            all_passed = False
    
    print()
    if all_passed:
        print("All smoke tests PASSED!")
        return 0
    else:
        print("Some smoke tests FAILED!")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
