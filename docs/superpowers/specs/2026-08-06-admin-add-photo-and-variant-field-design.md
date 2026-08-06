# Admin Add Photo and Variant Field Design

## Goal

Align the Add Product variant dropdown and photo-source chooser with the existing Liz Kitchen admin theme.

## Variant Field

- Use the Material exposed-dropdown style already used by the Detail Product form.
- Keep the existing fixed options and validation unchanged.
- Match the text inset and field height of the stock and price fields.

## Photo Source

- Replace the basic camera/gallery alert with the existing themed `dialog_photo_source` bottom sheet.
- Set the title to **Tambah Foto Kue** and the description to **Pilih sumber foto produk yang ingin digunakan.**
- Reuse the existing gallery, camera, and cancel actions.
- Gallery and camera permissions and file handling remain unchanged.

## Verification

- Build the debug APK and run existing unit tests.
- Verify the variant text aligns with stock and price.
- Verify gallery, camera, and cancel actions dismiss the bottom sheet and trigger the existing flows.
