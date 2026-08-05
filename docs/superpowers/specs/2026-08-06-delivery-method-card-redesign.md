# Redesign Card Pemilihan Metode

## Tujuan

Menyederhanakan bottom sheet pemilihan metode agar informasi yang relevan melekat
pada masing-masing pilihan dan lebih mudah dipindai pada layar kecil.

## Card Pesan Antar

Card menampilkan:

- judul `Pesan Antar` dan deskripsi singkat;
- chip jarak dari cabang terdekat;
- chip ongkir;
- chip estimasi maksimal satu hari;
- nama dan alamat cabang pengiriman terdekat;
- penanda visual rekomendasi ketika delivery tersedia.

Menekan card langsung memilih `Pesan Antar` dan menutup bottom sheet. Saat alamat
di luar jangkauan, card tetap terlihat tetapi disabled dengan penjelasan bahwa
delivery tidak tersedia.

## Card Ambil di Cabang

Card menampilkan judul, deskripsi, dan chip `Tanpa ongkir`. Menekan card membuka
pilihan cabang yang sudah ada. Pemilihan cabang tetap menutup bottom sheet dan
mengirim metode serta cabang ke halaman checkout.

## Informasi Sekunder

Notifikasi jangkauan tetap ditampilkan secara ringkas. Rincian tarif dipindahkan
ke kontrol `Lihat rincian tarif pengiriman` dan tidak mendominasi tampilan awal.
Card cabang terdekat yang terpisah dihapus karena informasinya sudah berada di
dalam card `Pesan Antar`.

## State dan Data

Data jarak, ongkir, cabang terdekat, dan ketersediaan tetap memakai nilai yang
sudah diteruskan oleh `DetailCartFragment`. Tidak ada data Firebase baru.

## Verifikasi

- Delivery tersedia menampilkan jarak, ongkir, estimasi, dan cabang dengan benar.
- Delivery di luar jangkauan menampilkan card disabled.
- Card pickup membuka pilihan cabang.
- Pilihan delivery dan pickup tetap dikirim ke checkout.
- Rincian tarif dapat dibuka dan ditutup.
- Teks tidak terpotong pada layar sempit.
- Build Android berhasil.
