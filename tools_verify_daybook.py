# Standalone regression checks for the cash/bank movement rules used by LedgerRepository.
def cash_net(payments, entries):
    payment_net = sum(amount if direction == 'IN' else -amount for mode, direction, amount in payments if mode == 'CASH')
    entry_net = sum(amount if kind in ('OPENING', 'CASH_WITHDRAWAL') else -amount
                    for mode, kind, amount in entries if mode == 'CASH')
    return payment_net + entry_net

def bank_net(payments, entries):
    payment_net = sum(amount if direction == 'IN' else -amount for mode, direction, amount in payments if mode == 'BANK')
    entry_net = sum(amount if mode == 'BANK' and kind == 'OPENING' else
                    -amount if mode == 'BANK' and kind == 'EXPENSE' else
                    amount if kind == 'CASH_DEPOSIT' else
                    -amount if kind == 'CASH_WITHDRAWAL' else 0
                    for mode, kind, amount in entries)
    return payment_net + entry_net

# ₹1,000 opening cash, ₹300 customer cash receipt, ₹100 cash expense,
# ₹200 cash deposited to bank => cash ₹1,000; bank ₹200.
payments = [('CASH', 'IN', 30_000)]
entries = [('CASH', 'OPENING', 100_000), ('CASH', 'EXPENSE', 10_000),
           ('CASH', 'CASH_DEPOSIT', 20_000)]
assert cash_net(payments, entries) == 100_000
assert bank_net([], entries) == 20_000
# Bank withdrawal increases cash and decreases bank.
entries.append(('CASH', 'CASH_WITHDRAWAL', 5_000))
assert cash_net(payments, entries) == 105_000
assert bank_net([], entries) == 15_000
# UPI movement must not affect cash.
assert cash_net([('CASH', 'IN', 30_000), ('UPI', 'IN', 99_000)], entries) == 105_000
print('Day Book cash/bank regression checks: PASS')
