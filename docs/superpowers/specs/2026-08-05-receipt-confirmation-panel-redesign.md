# Redesign Panel Konfirmasi Penerimaan

## Tujuan

Membuat aksi upload bukti penerimaan atau pengambilan lebih mudah dipindai,
informatif, dan tetap senada dengan tema Liz Kitchen.

## Tampilan

Saat status pesanan `Sudah Diantar` atau `Siap Diambil`, footer menampilkan:

1. Eyebrow `Konfirmasi Penerimaan` atau `Konfirmasi Pengambilan`.
2. Judul `Pesanan sudah sampai?` atau `Pesanan sudah diambil?`.
3. Instruksi singkat bahwa foto diperlukan agar admin dapat menyelesaikan pesanan.
4. Card tenggat dua kolom:
   - kiri: label `Batas waktu` dan tanggal `dd-MM-yyyy, HH:mm`;
   - kanan: label `Sisa waktu` dan durasi seperti `2 hari 23 jam`.
5. Tombol penuh `Upload Foto Penerimaan` atau `Upload Foto Pengambilan`.

Footer pembayaran dan status lain tetap menggunakan tampilan aksi yang sekarang.

## State Data

Jika `autoCompletionDeadlineAtMillis` tersedia, card menampilkan deadline dan
sisa waktu. Jika deadline belum tersedia, card tetap terlihat dengan nilai
`Menunggu sinkronisasi` agar user memahami bahwa tenggat sedang disiapkan dan
bukan mengira fitur hilang.

## Implementasi

Layout menerima panel konfirmasi khusus yang tersembunyi secara default. Fragment
menampilkan panel tersebut hanya untuk `Sudah Diantar` dan `Siap Diambil`, serta
menyembunyikan teks aksi generik. Formatter sisa waktu yang sudah ada tetap
digunakan; hanya presentasi teks yang dipisahkan ke view masing-masing.

## Verifikasi

- Delivery dan pickup menampilkan copy yang sesuai.
- Deadline menggunakan format `dd-MM-yyyy, HH:mm`.
- Sisa waktu berada di kolom kanan dan tidak terpotong pada layar sempit.
- State deadline kosong tetap informatif.
- Flow pembayaran dan tombol aksi lain tidak berubah.
- Build Android berhasil.
