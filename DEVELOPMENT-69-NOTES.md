# Development 69 - Sale Customer Empty-State Tap Action

- Fixed the Sale / New Bill customer picker empty state.
- When no customer is saved, the message itself now clearly invites the user to tap it to add a customer.
- Tapping that message opens the existing inline New Customer form, without requiring the user to find the separate Add New Party button.
- After saving, the new customer is selected automatically in the current sale invoice (existing behavior retained).
- The message action is inactive after a customer is selected; customer selection, search, outstanding balance, and Purchase flow are unchanged.
