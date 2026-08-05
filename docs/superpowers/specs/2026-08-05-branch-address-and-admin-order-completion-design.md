# Desain Alamat Cabang dan Penyelesaian Pesanan oleh Admin

## Tujuan

1. Menampilkan cabang yang memproses pesanan secara jelas pada detail pesanan user dan admin.
2. Mengubah konfirmasi penerimaan user menjadi bukti yang harus diselesaikan oleh admin.
3. Mempertahankan penyelesaian otomatis setelah tiga hari hanya ketika user belum mengunggah bukti.

## Informasi Cabang

- Detail pesanan user dan admin menampilkan bagian khusus `Cabang Dipilih`.
- Bagian tersebut berisi nama dan alamat cabang.
- Informasi cabang tampil untuk metode `Pesan Antar` dan `Ambil Sendiri`.
- Data memakai field snapshot order `pickupBranchId`, `pickupBranchName`, dan `pickupBranchAddress`.
- Untuk `Pesan Antar`, checkout menyimpan cabang terdekat dari hasil geocode sebagai cabang pemroses.
- User tidak memilih cabang delivery secara manual.
- Bottom sheet metode menampilkan nama cabang terdekat, alamat, jarak, dan ongkir sebelum user memilih `Pesan Antar`.
- Ringkasan checkout menampilkan cabang terdekat yang akan memproses pengiriman.
- Untuk `Ambil Sendiri`, checkout tetap menyimpan cabang yang dipilih user.
- Helper fallback cabang yang sudah ada hanya dipakai untuk order lama yang belum memiliki snapshot cabang.
- Alamat penerima tetap ditampilkan terpisah untuk metode `Pesan Antar`.

## Flow Bukti User

- Tombol user tetap bernama `Pesanan Diterima` untuk pesan antar dan `Sudah Diambil` untuk pickup.
- User tetap wajib memilih foto dari kamera atau galeri.
- Setelah upload berhasil, sistem menyimpan `receiptProofUrl` dan `receiptProofUploadedAtMillis` pada order global dan salinan order user.
- Status berubah menjadi `Menunggu Penyelesaian Admin`, bukan langsung `Selesai`.
- Status ini memberi tahu user bahwa bukti sudah diterima dan sedang menunggu penyelesaian admin.
- User tidak diminta mengunggah bukti kedua kali selama URL bukti sudah tersedia.

## Flow Admin

- Saat status `Menunggu Penyelesaian Admin` dan `receiptProofUrl` tersedia, detail admin menampilkan bukti user serta tombol `Pesanan Selesai`.
- Admin dapat membuka atau mengunduh bukti menggunakan preview yang sudah ada.
- Tombol hanya aktif jika bukti user tersedia.
- Admin tidak perlu mengunggah foto tambahan.
- Klik tombol memperbarui kedua dokumen order menjadi:
  - `status`: `Selesai`
  - `completionType`: `USER_PROOF`
  - `completionLabel`: `Diterima Customer dengan Bukti`
- Foto user tetap menjadi bukti final pada riwayat pesanan.

## Penyelesaian Otomatis

- Hitungan tiga hari tetap dimulai dari status `Sedang Dikirim` atau `Siap Diambil` yang memiliki bukti admin.
- Scheduler hanya menyelesaikan order jika tenggat lewat dan `receiptProofUrl` masih kosong.
- Jika user sudah mengunggah bukti, scheduler tidak menyelesaikan order; order menunggu admin.
- Order tanpa bukti user yang melewati tenggat menjadi:
  - `status`: `Selesai`
  - `completionType`: `AUTO_SYSTEM`
  - `completionLabel`: `Diterima Otomatis oleh Sistem`
- Flow otomatis berlaku untuk pesan antar dan pickup.

## Kompatibilitas

- Tambahkan konstanta status `Menunggu Penyelesaian Admin` pada Android.
- Order lama yang sudah `Selesai` tidak diubah.
- Order lama dengan bukti user tetapi belum selesai akan menampilkan aksi admin setelah dibaca, selama statusnya masih `Sedang Dikirim` atau `Siap Diambil`; UI admin memperlakukan kondisi tersebut sama dengan status baru agar bukti tidak terabaikan.
- Format Firestore tetap memakai koleksi global `orders/{orderId}` dan salinan `users/{userId}/orders/{orderId}`.

## Verifikasi

- Unit test kelayakan auto-completion memastikan order dengan `receiptProofUrl` tidak diselesaikan scheduler.
- Uji upload bukti delivery dan pickup menghasilkan status `Menunggu Penyelesaian Admin` pada kedua dokumen.
- Uji tombol admin menyelesaikan kedua dokumen dengan label manual.
- Uji alamat cabang tampil pada detail user dan admin untuk kedua metode.
- Uji checkout delivery menyimpan cabang terdekat, bukan selalu memakai cabang default.
- Uji cabang terdekat tampil pada pemilihan metode dan ringkasan checkout.
- Jalankan `sh gradlew testDebugUnitTest assembleDebug --console=plain`, test Function, dan `git diff --check`.
