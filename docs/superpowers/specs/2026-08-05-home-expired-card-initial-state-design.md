# Home Expired Card Initial State

## Masalah

Card produk expired sudah diberi alpha redup saat bind, tetapi animator bawaan
RecyclerView mengembalikan alpha item ke nilai penuh setelah animasi masuk.
Akibatnya state disabled baru terlihat setelah item di-bind ulang oleh interaksi.

## Desain

Nonaktifkan `itemAnimator` hanya pada RecyclerView katalog home. Adapter tetap
menjadi sumber state visual expired, sehingga card expired langsung tampil redup
pada render pertama maupun bind ulang. Data, filter, navigasi, dan grid tidak berubah.

## Verifikasi

- Build debug Android berhasil.
- Saat halaman home pertama dibuka, card expired memiliki alpha redup.
- Card aktif tetap memiliki alpha penuh dan filter tetap bekerja.
