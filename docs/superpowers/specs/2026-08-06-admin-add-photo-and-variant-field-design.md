# Admin Photo Picker, Variant Field, and Camera Hardening Design

## Goal

Align the Add Product variant dropdown and photo-source chooser with the existing Liz Kitchen admin theme.
Harden every camera flow against URI permission, large preview, and fragment recreation issues.

## Variant Field

- Use the Material exposed-dropdown style already used by the Detail Product form.
- Keep the existing fixed options and validation unchanged.
- Match the text inset and field height of the stock and price fields.

## Photo Source

- Replace the basic camera/gallery alert with the existing themed `dialog_photo_source` bottom sheet.
- Set the title to **Tambah Foto Kue** and the description to **Pilih sumber foto produk yang ingin digunakan.**
- Reuse the existing gallery, camera, and cancel actions.
- Gallery and camera permissions and file handling remain unchanged.

## Camera Hardening

- Use `ActivityResultContracts.TakePicture()` for all four camera flows:
  - Add Product photo.
  - Edit Product photo.
  - User receipt proof.
  - Admin order-status proof.
- Create the destination with the existing temporary-image helper and a `FileProvider` URI based on the application package.
- Persist the pending camera file path through `savedInstanceState` so results survive fragment recreation.
- Accept a camera result only when the destination file exists and is not empty.
- Load product previews directly from the file with Glide instead of decoding a full-size bitmap.
- Keep gallery selection and Firebase upload behavior unchanged.

## Verification

- Build the debug APK and run existing unit tests.
- Verify the variant text aligns with stock and price.
- Verify gallery, camera, and cancel actions dismiss the bottom sheet and trigger the existing flows.
- Verify each camera result reaches its existing preview or upload path.
- Verify canceled or empty camera results are ignored without replacing the selected image.
