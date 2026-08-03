# Desain Satuan Produk, Validasi Angka, dan Penyelesaian Pesanan Otomatis

## Tujuan

1. Menghapus input satuan produk dan menetapkan seluruh produk serta varian menggunakan satuan `toples`.
2. Mencegah stok dan harga berisi angka tidak wajar, termasuk nol berulang di depan dan jumlah digit berlebihan.
3. Menyelesaikan pesanan otomatis setelah tiga hari apabila pelanggan tidak mengonfirmasi penerimaan atau pengambilan.

## Satuan Produk

- Form tambah dan edit produk tidak lagi menampilkan input satuan.
- Aplikasi memakai satu konstanta `toples` saat membuat, mengedit, membaca, dan menampilkan produk.
- Data produk lama yang masih menyimpan `pcs`, `pack`, atau satuan lain dinormalisasi menjadi `toples` ketika dibaca aplikasi.
- Data lama akan tersimpan sebagai `toples` saat produk berikutnya diedit. Tidak diperlukan proses migrasi massal terpisah.
- Snapshot keranjang dan pesanan, termasuk data lama, dinormalisasi menjadi `toples` ketika dibaca sehingga seluruh tampilan dan invoice konsisten.

## Validasi Stok dan Harga

- Stok menerima digit `0-9`, maksimal 6 digit, dengan rentang `0..999999`.
- Harga menerima digit `0-9`, maksimal 9 digit, dengan rentang `1..999999999`.
- Nol berulang di depan dibersihkan saat mengetik. Contoh: `00025` menjadi `25`; input yang hanya berisi nol menjadi `0`.
- Pemisah ribuan pada harga hanya merupakan format tampilan. Nilai yang divalidasi dan disimpan tetap berdasarkan digit mentah.
- Tombol tambah/update varian menolak nilai di luar rentang dan menampilkan pesan pada field terkait.
- Aturan yang sama dipakai pada form tambah dan edit produk melalui helper input yang sudah ada atau helper kecil bersama apabila memang diperlukan.

## Pemicu Penyelesaian Otomatis

Aturan berlaku untuk kedua metode pengambilan:

- `Pesan Antar`: hitungan dimulai saat admin menyimpan status `Sedang Dikirim` beserta bukti foto.
- `Ambil Sendiri`: hitungan dimulai saat admin menyimpan status `Siap Diambil` beserta bukti foto.

Saat status tersebut beserta bukti disimpan, Firestore trigger menggunakan waktu server dan menulis:

- `fulfillmentStartedAtMillis`: waktu server ketika status operasional valid diterima.
- `autoCompletionDeadlineAtMillis`: waktu server ditambah tiga hari.
- Bukti foto tetap tersimpan pada `statusProofs` seperti flow saat ini.

Jika user mengunggah bukti sebelum tenggat:

- Status menjadi `Selesai`.
- `completionType` bernilai `USER_PROOF`.
- `completionLabel` bernilai `Diterima Customer dengan Bukti`.
- Foto user tetap disimpan pada `receiptProofUrl`.

Jika user tidak mengunggah bukti sampai tenggat:

- Status menjadi `Selesai`.
- `completionType` bernilai `AUTO_SYSTEM`.
- `completionLabel` bernilai `Diterima Otomatis oleh Sistem`.
- `autoCompletedAt` diisi timestamp server.
- Tidak dibuat foto palsu atau URL bukti kosong yang dianggap sebagai foto.

Label penyelesaian ditampilkan pada detail pesanan user, detail admin, daftar/riwayat bukti, dan invoice.

## Firebase Cloud Function

- Tambahkan proyek Firebase Functions berbasis Node.js 20 dan Firebase Functions v2.
- Firestore trigger pada koleksi global `orders` menetapkan waktu mulai dan tenggat menggunakan clock server ketika status `Sedang Dikirim` atau `Siap Diambil` sudah memiliki bukti admin. Trigger juga menyinkronkan field tersebut ke salinan order user.
- Scheduled Function berjalan setiap satu jam dengan zona waktu `Asia/Jakarta`.
- Function membaca koleksi global `orders` yang statusnya `Sedang Dikirim` atau `Siap Diambil` dan tenggatnya sudah lewat.
- Sebelum update, function memeriksa ulang status, tenggat, keberadaan bukti status admin, dan memastikan `receiptProofUrl` masih kosong.
- Update dilakukan pada dokumen global `orders/{orderId}` dan salinan `users/{userId}/orders/{orderId}` menggunakan batch.
- Operasi bersifat idempotent: order yang sudah `Selesai` tidak diproses ulang.
- Ketepatan otomatisasi adalah tiga hari ditambah interval scheduler maksimal satu jam.
- Deploy scheduled function memerlukan Firebase project pada Blaze plan serta akses deploy Firebase CLI.

## Kompatibilitas Data

- Field baru pada model order memiliki default kosong atau nol agar pesanan lama tetap bisa dibaca.
- Pesanan lama tanpa `fulfillmentStartedAt` tidak otomatis diselesaikan. Tenggat baru dibuat ketika admin kembali menyimpan status operasional beserta bukti.
- Pembatalan, expiry pembayaran, dan pesanan yang sudah selesai tidak disentuh scheduler.

## Verifikasi

- Unit test untuk sanitasi leading zero, batas digit stok, dan batas digit harga.
- Unit test kelayakan auto-completion: status benar, tenggat lewat, bukti admin ada, bukti user belum ada.
- Build Android dengan `testDebugUnitTest` dan `assembleDebug`.
- Test Functions untuk order delivery, pickup, order belum jatuh tempo, order tanpa bukti admin, dan idempotensi.
- Uji manual tampilan label manual dan otomatis pada user, admin, riwayat bukti, dan invoice.
