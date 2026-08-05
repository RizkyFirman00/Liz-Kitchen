# Checkout Method Confirmation Design

## Goal

Keep the method selection sheet open while the user reviews delivery and pickup details. Apply the choice to checkout only after the user presses a confirmation button.

## Interaction

- Opening the sheet restores the checkout's currently selected method and pickup branch.
- If checkout has no existing choice, the sheet starts without an active method.
- Tapping **Pesan Antar** selects delivery without closing the sheet and clears any temporary pickup branch.
- Tapping **Ambil di Cabang** opens the branch list. Pickup becomes valid only after a branch is selected.
- Selecting a pickup branch highlights both the pickup card and the selected branch without closing the sheet.
- **Konfirmasi Pilihan** is enabled only for available delivery or pickup with a selected branch.
- Pressing confirmation sends the temporary choice through the existing listener and closes the sheet.
- Back, swipe, or tapping outside closes the sheet without changing the checkout selection.

## Implementation

- `DetailCartFragment` passes its current method and pickup branch into `MetodeAmbilFragment` before showing it.
- `MetodeAmbilFragment` owns temporary method and branch state while visible.
- Existing delivery availability, nearest-branch information, rate details, and callbacks remain unchanged.
- The layout adds one full-width themed confirmation button below the method controls.

## Verification

- Build and run existing debug unit tests.
- Verify that card and branch taps do not invoke the listener.
- Verify that confirmation invokes the listener once with the temporary valid choice.
- Verify that dismissing the sheet without confirmation preserves the previous checkout choice.
