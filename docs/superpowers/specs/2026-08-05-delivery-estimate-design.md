# Desain Informasi Estimasi Pesan Antar

## Tujuan

Memberi informasi yang konsisten bahwa pesanan dengan metode `Pesan Antar` diperkirakan tiba maksimal satu hari setelah pesanan mulai dikirim.

## Tampilan

- Bottom sheet pemilihan metode menampilkan `Estimasi tiba maksimal 1 hari setelah pesanan mulai dikirim` di area informasi delivery.
- Ringkasan checkout menampilkan estimasi setelah user memilih `Pesan Antar`.
- Detail pesanan user dan admin menampilkan baris `Estimasi Tiba` dengan nilai `Maksimal 1 hari setelah mulai dikirim`.
- Informasi disembunyikan untuk metode `Ambil Sendiri`.

## Data

- Estimasi adalah informasi tetap dan tidak disimpan ke Firebase.
- Tampilan ditentukan dari field `metodePengambilan` yang sudah ada.
- Tidak ada tanggal tiba absolut karena waktu produksi tidak termasuk dalam estimasi pengiriman.

## Verifikasi

- Uji delivery menampilkan estimasi pada pemilihan metode, checkout, detail user, dan detail admin.
- Uji pickup tidak menampilkan estimasi.
- Jalankan build Android dan `git diff --check`.
