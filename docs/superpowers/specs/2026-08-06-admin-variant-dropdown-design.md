# Admin Variant Dropdown Design

## Goal

Standardize variant entry on both admin product forms and prevent abnormally large stock values.

## Behavior

- **Tambah Kue** and **Detail Kue** use an exposed dropdown for variant names.
- The only selectable names are `250 gram`, `500 gram`, and `700 gram`.
- The dropdown is read-only; admins cannot enter arbitrary variant names.
- Editing a variant allows its name to be replaced with any dropdown option.
- Variant stock accepts digits only and is limited to three digits (`0` through `999`).
- Price input behavior and the fixed product unit `toples` remain unchanged.

## Implementation

- Reuse one shared variant-name option list in both admin fragments.
- Change the Add Product variant-name field to `MaterialAutoCompleteTextView`, matching Product Detail.
- Reuse the existing numeric input limiter with `maxDigits = 3` on both forms.

## Verification

- Build the debug APK and run existing unit tests.
- Verify both forms expose exactly three variant names.
- Verify stock input strips leading zeroes and cannot exceed three digits.
