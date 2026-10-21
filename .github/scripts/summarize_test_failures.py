#!/usr/bin/env python3
import glob
import os
import xml.etree.ElementTree as ET

def summarize():
    report_files = glob.glob("app/build/test-results/testDebugUnitTest/*.xml")
    if not report_files:
        return ""
    
    failures = []
    for report in report_files:
        try:
            tree = ET.parse(report)
            root = tree.getroot()
            for testcase in root.iter("testcase"):
                classname = testcase.attrib.get("classname", "")
                name = testcase.attrib.get("name", "")
                for failure in testcase.iter("failure"):
                    message = failure.attrib.get("message", "Failure")
                    text = failure.text or ""
                    first_line = text.strip().split("\n")[0] if text else message
                    failures.append(f"- **{classname}.{name}**: {message}\n  `{first_line}`")
        except Exception:
            continue
    
    return "\n".join(failures)

if __name__ == "__main__":
    summary = summarize()
    if summary:
        print(summary)
