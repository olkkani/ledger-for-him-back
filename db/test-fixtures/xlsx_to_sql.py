#!/usr/bin/env python3
"""편한가계부 xlsx -> transactions INSERT SQL.

Usage: python3 db/test-fixtures/xlsx_to_sql.py INPUT.xlsx [-o OUT.sql] [--user-id 1] [--id-start 1] [--category 미분류]
Requires: pip install openpyxl

Mapping:
  날짜 -> occurred_at (Asia/Seoul)    분류 -> description    내용 -> memo
  KRW + 수입/지출 -> amount (수입 +, 지출 -)
"""
import argparse
from pathlib import Path

import openpyxl


def q(s):
    return "'" + s.replace("'", "''") + "'"


def main():
    p = argparse.ArgumentParser()
    p.add_argument("xlsx")
    p.add_argument("-o", "--out")
    p.add_argument("--user-id", type=int, default=1)
    p.add_argument("--id-start", type=int, default=1)
    p.add_argument("--category", default="미분류", help="category is NOT NULL but absent in the sheet")
    a = p.parse_args()

    ws = openpyxl.load_workbook(a.xlsx).active
    rows = list(ws.iter_rows(min_row=2, values_only=True))
    rows.reverse()  # sheet is newest-first; insert chronologically

    values = []
    for i, r in enumerate(rows):
        date, _, desc, _, memo, krw, kind = r[:7]
        if date is None or not krw:
            continue
        amount = abs(krw) * (1 if kind == "수입" else -1)
        values.append(
            f"({a.id_start + i}, {a.user_id}, {amount:.2f}, {q(str(desc or ''))}, "
            f"{q(str(memo)) if memo else 'NULL'}, {q(a.category)}, "
            f"'{date:%Y-%m-%d %H:%M:%S}+09:00')"
        )

    sql = (
        "INSERT INTO transactions (id, user_id, amount, description, memo, category, occurred_at) VALUES\n"
        + ",\n".join(values)
        + "\nON CONFLICT ON CONSTRAINT uq_transactions_dedupe DO NOTHING;\n"
    )
    out = Path(a.out) if a.out else Path(__file__).with_suffix(".sql")
    out.write_text(sql, encoding="utf-8")
    print(f"{len(values)} rows -> {out}")


if __name__ == "__main__":
    main()
