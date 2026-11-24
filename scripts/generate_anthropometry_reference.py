#!/usr/bin/env python3
"""Generate the optional offline SAP bundle and parity cases from the backend export.

From the backend, run poetry run python -m scripts.export_anthropometry_reference
--ruleset-version VERSION --output /path/to/mobile/scripts/anthropometry/reference.json.
Then run this script from Mobile.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path

PACKAGE = "ar.com.ingenieriaalimentacion.domain.usecase.measurement"
ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = Path("kotlin/ar/com/ingenieriaalimentacion/domain/usecase/measurement")
DEFAULT_INPUT = ROOT / "scripts/anthropometry/reference.json"
INDICATORS = {"length_for_age", "weight_for_age", "bmi_for_age", "height_for_age"}
SEVERITIES = {"medium", "high", "critical", None}


def canonical_json(value):
    return json.dumps(value, ensure_ascii=False, allow_nan=False, separators=(",", ":"), sort_keys=True)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def text(value):
    return isinstance(value, str) and bool(value.strip())


def finite(value):
    return type(value) in {float, int} and math.isfinite(value)


def validate_ruleset(ruleset):
    require(ruleset["schema_version"] == 1, "Unsupported rule schema")
    require(text(ruleset["version"]), "Missing rule version")
    cap = ruleset["pediatric_max_month"]
    require(type(cap) is int and cap >= 24, "Invalid pediatric age limit")
    occupied = set()
    indicators = set()
    for ref in ruleset["references"]:
        indicator = ref["indicator"]
        indicators.add(indicator)
        require(indicator in INDICATORS, f"Unknown indicator {indicator}")
        require(text(ref["reference_version"]) and text(ref["source"]), f"Missing provenance for {indicator}")
        first, last = ref["first_month"], ref["last_month"]
        lower, upper = (0, 23) if indicator in {"length_for_age", "weight_for_age"} else (24, cap)
        require(type(first) is int and type(last) is int and lower <= first <= last <= upper, "Invalid reference range")
        months = {(indicator, month) for month in range(first, last + 1)}
        require(not (months & occupied), f"Overlapping reference range for {indicator}")
        occupied.update(months)
        age_unit = ref["age_unit"]
        require(age_unit in {"days", "months"}, "Unsupported reference age unit")
        first_day, last_day = ref.get("first_day"), ref.get("last_day")
        if age_unit == "days":
            require(type(first_day) is int and type(last_day) is int and 0 <= first_day <= last_day,
                    "Daily reference must declare the supported day range")
        else:
            require(first_day is None and last_day is None, "Monthly reference cannot declare a day range")
        require(ref["formula"] in {"lms", "lms_extended", "standard"}, "Unsupported standardization formula")
        method = {"length_for_age": "recumbent", "height_for_age": "standing"}.get(indicator)
        require(ref.get("stature_method") == method, "Invalid stature method")
        offset = ref.get("alternate_method_offset_cm")
        require(offset is None or method is not None and finite(offset), "Invalid alternate stature offset")
        rows = set()
        for row in ref["rows"]:
            key = row["sex"], row["age"]
            require(key[0] in {"female", "male"} and type(key[1]) is int and key[1] >= 0 and key not in rows,
                    f"Invalid or duplicate reference row {key}")
            require(all(finite(row[field]) for field in ("lambda", "median", "sigma")) and
                    row["median"] > 0 and row["sigma"] > 0, f"Invalid reference parameters {key}")
            rows.add(key)
        for sex in ("male", "female"):
            ages = {age for row_sex, age in rows if row_sex == sex}
            require(ages and len(ages) == max(ages) - min(ages) + 1, f"Missing reference rows for {indicator}")
            if age_unit == "months":
                require(min(ages) <= first and max(ages) >= last, f"Monthly rows do not cover {indicator}")
            else:
                require(min(ages) <= first_day and max(ages) >= last_day, f"Daily rows do not cover {indicator}")
        bands = ref["bands"]
        require(bands and bands[0]["lower"] is None and bands[-1]["upper"] is None, "Incomplete score intervals")
        for i, band in enumerate(bands):
            require(text(band["classification"]) and band["severity"] in SEVERITIES, "Invalid classification")
            require(type(band["include_lower"]) is bool and type(band["include_upper"]) is bool,
                    "Invalid score boundary flags")
            lo, hi = band["lower"], band["upper"]
            require((lo is None or finite(lo)) and (hi is None or finite(hi)), "Invalid score boundary")
            require(lo is None or hi is None or lo < hi, "Empty score interval")
            if i:
                previous = bands[i - 1]
                require(lo is not None and previous["upper"] == lo and
                        previous["include_upper"] != band["include_lower"], "Missing or overlapping score boundary")
    require(indicators == INDICATORS, "Incomplete indicator set")
    for indicator in INDICATORS:
        lower, upper = (0, 23) if indicator in {"length_for_age", "weight_for_age"} else (24, cap)
        require(all((indicator, age) in occupied for age in range(lower, upper + 1)),
                f"Incomplete product age range for {indicator}")


def kotlin_string(value):
    return json.dumps(value, ensure_ascii=False).replace("$", r"\$")


def kotlin_json(value):
    if value is None:
        return "null"
    raw = canonical_json(value)
    parts = [kotlin_string(raw[start:start + 8000]) for start in range(0, len(raw), 8000)]
    if len(parts) == 1:
        return parts[0]
    return "listOf(\n" + "".join(f"        {part},\n" for part in parts) + '    ).joinToString("")'


def generate(artifact):
    ruleset, commit, digest, cases = (None, None, None, None)
    if artifact is not None:
        ruleset, commit, digest, cases = (artifact[key] for key in ("ruleset", "backend_commit", "sha256", "cases"))
        validate_ruleset(ruleset)
        require(isinstance(cases, list) and cases, "Missing backend parity cases")
        require(text(commit) and text(digest), "Missing backend artifact provenance")
        payload = {key: artifact[key] for key in ("ruleset", "cases", "backend_commit")}
        require(hashlib.sha256(canonical_json(payload).encode()).hexdigest() == digest, "Backend artifact digest differs")
    header = f"package {PACKAGE}\n\n// Generated by scripts/generate_anthropometry_reference.py.\n"
    main = header + (
        "internal object BundledSapReference {\n"
        f"    val rulesetJson: String? = {kotlin_json(ruleset)}\n"
        f"    val backendCommit: String? = {kotlin_json(commit)}\n"
        f"    val sha256: String? = {kotlin_json(digest)}\n"
        "}\n"
    )
    tests = header + f"internal val bundledSapParityCasesJson: String? = {kotlin_json(cases)}\n"
    return {
        ROOT / "composeApp/src/commonMain" / SOURCE_DIR / "BundledSapReference.kt": main,
        ROOT / "composeApp/src/commonTest" / SOURCE_DIR / "BundledSapParityCases.kt": tests,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    try:
        if args.input.exists():
            artifact = json.loads(args.input.read_text())
        elif args.input == DEFAULT_INPUT:
            artifact = None
        else:
            raise ValueError(f"Reference artifact missing: {args.input}")
        for path, expected in generate(artifact).items():
            if args.check:
                require(path.exists() and path.read_text() == expected, f"Generated reference differs: {path}")
            else:
                path.write_text(expected)
    except (KeyError, TypeError, ValueError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
