"""
CI 요약: 테스트 결과(build/test-results)와 JaCoCo 커버리지(jacocoTestReport.xml)를 마크다운 표로
- backend 폴더에서 실행 (GitHub Actions의 working-directory)
- 출력은 $GITHUB_STEP_SUMMARY 로 보내서 Actions 실행 화면에 표로 보임
"""
import glob
import os
import xml.etree.ElementTree as ET

print("## 🧪 테스트 결과")

tests = failures = skipped = 0
failed_names = []
for path in glob.glob("build/test-results/test/*.xml"):
    root = ET.parse(path).getroot()
    tests += int(root.get("tests", 0))
    failures += int(root.get("failures", 0)) + int(root.get("errors", 0))
    skipped += int(root.get("skipped", 0))
    for case in root.iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            failed_names.append(f"{root.get('name', '').split('.')[-1]}.{case.get('name')}")

if tests == 0:
    print("테스트 결과 파일이 없어요 (컴파일 단계에서 실패했을 수 있어요).")
else:
    mark = "✅" if failures == 0 else "❌"
    print(f"{mark} 전체 {tests}개 · 성공 {tests - failures - skipped}개 · 실패 {failures}개 · 건너뜀 {skipped}개")
    for name in failed_names[:20]:
        print(f"- ❌ `{name}`")

print()
print("## 📊 커버리지 (JaCoCo)")

report = "build/reports/jacoco/test/jacocoTestReport.xml"
if not os.path.exists(report):
    print("커버리지 보고서가 없어요.")
    raise SystemExit(0)

root = ET.parse(report).getroot()


def rate(node, kind):
    for c in node.findall("counter"):
        if c.get("type") == kind:
            covered, missed = int(c.get("covered")), int(c.get("missed"))
            total = covered + missed
            return covered, total
    return 0, 0


def pct(covered, total):
    return f"{covered * 100 / total:.1f}%" if total else "-"


line_c, line_t = rate(root, "LINE")
branch_c, branch_t = rate(root, "BRANCH")
print(f"**전체** 줄 {pct(line_c, line_t)} ({line_c}/{line_t}) · 분기 {pct(branch_c, branch_t)} ({branch_c}/{branch_t})")
print()
print("| 패키지 | 줄 커버리지 | 분기 커버리지 |")
print("|---|---:|---:|")

rows = []
for pkg in root.findall("package"):
    lc, lt = rate(pkg, "LINE")
    bc, bt = rate(pkg, "BRANCH")
    if lt == 0:
        continue
    name = pkg.get("name").replace("com/sklabs1037/sklabs1037/", "").replace("/", ".")
    rows.append((lc / lt, name, pct(lc, lt), pct(bc, bt)))

for _, name, lp, bp in sorted(rows, reverse=True):
    print(f"| {name} | {lp} | {bp} |")
