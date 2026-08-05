# Status Sudah Diantar dan Filter Admin

## Tujuan

Memulai tenggat konfirmasi penerimaan pada waktu yang tepat dan membuat filter
status pesanan admin tetap mudah digunakan saat jumlah status bertambah.

## Alur Pesan Antar

1. Admin mengubah pesanan dari `Diproses` menjadi `Sedang Dikirim` dengan bukti
   proses pengiriman. Counter belum dimulai.
2. Setelah pesanan sampai, admin mengunggah bukti proses dan mengubah status
   menjadi `Sudah Diantar`.
3. Cloud Function menyimpan waktu mulai dan deadline tiga hari saat status
   `Sudah Diantar` beserta buktinya tersedia.
4. User melihat permintaan upload foto penerimaan dan sisa waktu pada detail
   pesanan.
5. Setelah user mengunggah foto, status menjadi `Menunggu Penyelesaian Admin`.
   Admin memeriksa foto lalu menyelesaikan pesanan.
6. Jika user tidak mengunggah foto sampai deadline, Cloud Function menyelesaikan
   pesanan otomatis.

## Alur Ambil Sendiri

Alur tetap menggunakan `Siap Diambil`. Counter tiga hari dimulai setelah admin
menyimpan status tersebut beserta bukti proses. Foto pengambilan user tetap
menjadi dasar penyelesaian manual oleh admin.

## Filter Status Admin

Tab status horizontal diganti dropdown tunggal dengan label status aktif.
Dropdown memuat seluruh status, termasuk `Sudah Diantar`, dan ditempatkan bersama
kontrol urutan. Pencarian, pengurutan, ringkasan jumlah, dan perilaku scroll daftar
tetap dipertahankan.

## Konsistensi Data

- Status `Sudah Diantar` ditambahkan sebagai konstanta bersama Android.
- Bukti status disimpan pada `statusProofs["Sudah Diantar"]`.
- Cloud Function hanya memulai counter untuk `Sudah Diantar` dan `Siap Diambil`.
- Dokumen global dan dokumen pesanan user menerima deadline dan perubahan status
  yang sama.
- Pesanan lama dengan status `Sedang Dikirim` tidak otomatis mendapat deadline
  sampai admin memindahkannya ke `Sudah Diantar`.

## Verifikasi

- Unit test Cloud Function memastikan `Sedang Dikirim` tidak eligible,
  sedangkan `Sudah Diantar` dan `Siap Diambil` dengan bukti eligible.
- Build dan unit test Android berhasil.
- Dropdown dapat memfilter setiap status dan menampilkan `Sudah Diantar`.
- User hanya melihat aksi upload penerimaan setelah `Sudah Diantar` atau
  `Siap Diambil`.
