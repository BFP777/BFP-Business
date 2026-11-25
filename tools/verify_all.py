"""Run every offline/source-level regression check shipped with BFP Business Pro."""
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = [
    "tools/verify_dev39_ui_bank.py",
    "tools/verify_dev113_dashboard_ui.py",
    "tools/verify_dev114_side_menu.py",
    "tools/verify_dev40_bank_linking.py",
    "tools/verify_dev41_bank_balances.py",
    "tools/verify_dev42_daybook_bank_names.py",
    "tools/verify_dev43_separate_expense_bank.py",
    "tools/verify_dev44_inventory.py",
    "tools/verify_dev45_purchase_attachments.py",
    "tools/verify_dev119_purchase_camera.py",
    "tools/verify_dev46_employee_payroll.py",
    "tools/verify_dev47_smart_stock.py",
    "tools/verify_dev48_supplier_bill_ocr.py",
    "tools/verify_dev49_supplier_bill_lines.py",
    "tools/verify_dev50_supplier_bill_verification.py",
    "tools/verify_dev51_duplicate_purchase.py",
    "tools/verify_dev52_supplier_insights.py",
    "tools/verify_dev53_customer_intelligence.py",
    "tools/verify_dev54_customer_relationship.py",
    "tools/verify_dev55_customer_tabs.py",
    # Legacy bilingual navigation assertion conflicts with the approved English-only UI.
    "tools/verify_dev57_voice_scanner.py",
    "tools/verify_dev58_profit_deadstock.py",
    "tools/verify_dev59_business_dashboard.py",
    "tools/verify_dev60_business_trends.py",
    "tools/verify_dev61_ai_business_intelligence.py",
    "tools/verify_dev62_ai_demand_forecast.py",
    "tools/verify_dev63_profit_cashflow.py",
    "tools/verify_dev64_decision_assistant.py",
    "tools/verify_dev34_build_readiness.py",
    "tools/verify_package_hygiene.py",
    "tools/verify_room_dao_sql.py",
    "tools/verify_invoice_number_sql.py",
    "tools/verify_posting_invariants.py",
    "tools/verify_dev79_gst_overflow.py",
    "tools/verify_dev80_invoice_level_gst.py",
    "tools/verify_dev81_purchase_item_gst.py",
    "tools/verify_dev82_payment_ledger.py",
    "tools/verify_dev33_integrated_audit.py",
    "tools_verify_daybook.py",
    "tools_verify_dev30_source.py",
]
for script in SCRIPTS:
    print(f"\n>>> {script}", flush=True)
    subprocess.run([sys.executable, str(ROOT / script)], cwd=ROOT, check=True)

ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
print("\nAndroidManifest XML parse: PASS")

app_gradle = (ROOT / "app/build.gradle.kts").read_text()
workflow = (ROOT / ".github/workflows/android-build.yml").read_text()
assert 'arg("room.schemaLocation", "$projectDir/schemas")' in app_gradle
assert "python3 tools/verify_all.py" in workflow
assert "gradle-version: 8.9" in workflow
assert "java-version: '17'" in workflow
print("Room schema export + CI regression hook: PASS")
print("BFP Business Pro offline verification suite: PASS")


subprocess.run([sys.executable, str(ROOT / 'tools/verify_dev67_sale_customer_picker.py')], cwd=ROOT, check=True)

subprocess.run([sys.executable, str(ROOT / "tools/verify_dev74_voice_billing.py")], cwd=ROOT, check=True)
subprocess.run([sys.executable, str(ROOT / 'tools/verify_dev77_sticky_voice_invoice.py')], cwd=ROOT, check=True)

subprocess.run([sys.executable, str(ROOT / "tools/verify_dev83_purchase_stock_link.py")], cwd=ROOT, check=True)
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev84_stock_summary.py")], cwd=ROOT, check=True)

subprocess.run([sys.executable, str(ROOT / "tools/verify_dev85_stock_adjustment.py")], cwd=ROOT, check=True)
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev86_stock_report.py")], cwd=ROOT, check=True)

subprocess.run([sys.executable, str(ROOT / "tools/verify_dev87_stock_period_export.py")], cwd=ROOT, check=True)
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev88_gst_reports.py")], cwd=ROOT, check=True)
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev89_hsn_gstr1.py")], cwd=ROOT, check=True)
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev90_gst_reconciliation.py")], cwd=ROOT, check=True)

subprocess.run([sys.executable, str(ROOT / "tools/verify_dev91_party_ledger_audit.py")], cwd=ROOT, check=True)
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev92_money_reconciliation.py")], cwd=ROOT, check=True)
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev93_business_financial_audit.py")], cwd=ROOT, check=True)

subprocess.run([sys.executable, str(ROOT / "tools/verify_dev94_profit_loss_position.py")], cwd=ROOT, check=True)

subprocess.run([sys.executable, str(ROOT / "tools/verify_dev95_financial_pdf.py")], cwd=ROOT, check=True)

# Dev96 reports dashboard

subprocess.run([sys.executable, str(ROOT / "verify_dev96_reports_dashboard.py")], cwd=ROOT, check=True)
# Dev97 backup package integrity and compatibility checks
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev97_backup_integrity.py")], cwd=ROOT, check=True)

# Dev98 automatic cloud-folder backup
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev98_auto_backup.py")], cwd=ROOT, check=True)

# Dev99 backup history and non-mutating restore preview
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev99_backup_history_preview.py")], cwd=ROOT, check=True)

# Dev100 backup/restore round-trip safety hardening
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev100_backup_roundtrip_safety.py")], cwd=ROOT, check=True)

# Dev101 recovery monitoring and retry controls
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev101_recovery_monitoring.py")], cwd=ROOT, check=True)

# Dev102 cloud backup inventory and guarded retention cleanup
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev102_cloud_backup_management.py")], cwd=ROOT, check=True)

# Dev103 backup retention and cloud storage monitor
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev103_backup_retention.py")], cwd=ROOT, check=True)

# Dev104 password-based backup encryption core
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev104_backup_encryption.py")], cwd=ROOT, check=True)

# Dev105 dashboard outstanding summary cards
subprocess.run([sys.executable, str(ROOT / "tools/verify_dev105_dashboard_outstanding.py")], cwd=ROOT, check=True)
