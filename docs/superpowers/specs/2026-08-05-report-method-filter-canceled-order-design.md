# Filter Metode Laporan Tanpa Pesanan Dibatalkan

## Aturan

Filter `Ambil Sendiri` dan `Pesan Antar` pada laporan penjualan hanya menampilkan
pesanan dengan metode yang sesuai dan status selain `Dibatalkan`.

Filter `Semua` dan filter status lain tidak berubah. Filter `Dibatalkan` tetap
menampilkan seluruh pesanan yang dibatalkan. Jumlah pesanan, total, daftar, dan
PDF menggunakan hasil filter yang sama.

## Verifikasi

- Pesanan dibatalkan tidak muncul pada filter `Ambil Sendiri`.
- Pesanan dibatalkan tidak muncul pada filter `Pesan Antar`.
- Pesanan tersebut tetap muncul pada filter `Semua` dan `Dibatalkan`.
- Build Android berhasil.
