<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="96" alt="ChatFin logo" />

# ChatFin

**Aplikasi keuangan pribadi Android — offline-first, dengan asisten AI "Sakurajima Mai"**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)](https://developer.android.com/studio/releases/platforms)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## Tentang ChatFin

ChatFin adalah aplikasi pencatatan keuangan pribadi untuk Android yang **berjalan penuh tanpa API key** — pencatatan transaksi, saldo dompet, kategori, budget, analitik, export, dan backup semuanya 100% offline. Lapisan AI (Gemini, opsional) hanya menambah kenyamanan lewat asisten bernama **Sakurajima Mai**, bukan menjadi fondasi aplikasi.

Dirancang untuk dua jenis pengguna:

- **Pemula** — tidak familiar dengan istilah keuangan, alur utama (catat transaksi → lihat saldo) harus jalan tanpa jargon atau pesan error teknis.
- **Melek finansial** — butuh akurasi angka, export rapi, budget per kategori, dan tren pengeluaran/pemasukan.

## Fitur

- 💬 **Chat AI (Mai)** — catat transaksi & tanya kondisi keuangan lewat bahasa natural, dengan fallback ke Mode Bot bila tanpa API key.
- 💰 **Manajemen transaksi & dompet** — saldo selalu konsisten dengan riwayat transaksi (operasi atomik).
- 🧾 **Scan struk (OCR)** — ambil foto struk belanja, transaksi terisi otomatis lewat ML Kit Text Recognition.
- 🏷️ **Kategori & budget** — atur batas pengeluaran per kategori.
- 📊 **Dashboard & analitik** — grafik tren, ringkasan bulanan, insight yang dihitung lokal di perangkat.
- 📤 **Export & backup** — ekspor data keuangan tanpa bergantung layanan luar.
- 🔐 **Autentikasi & sinkronisasi opsional** — Firebase Auth + Firestore untuk yang ingin sinkron lintas perangkat.

## Tech Stack

| Layer | Teknologi |
|---|---|
| Bahasa | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Arsitektur | MVVM (UI → ViewModel → Repository → DAO) |
| Database lokal | Room (`chatfin_database`) |
| Dependency Injection | Hilt |
| Konkurensi | Coroutines + Flow |
| Penyimpanan preferensi | DataStore (non-sensitif) / EncryptedSharedPreferences (sensitif) |
| Cloud (opsional) | Firebase Auth + Firestore |
| AI (opsional) | Gemini API |
| Grafik | Vico |
| OCR | ML Kit Text Recognition + CameraX |

## Struktur Proyek

Package-by-feature dengan dependensi satu arah (Composable tidak menyentuh DAO/Repository langsung; ViewModel tidak tahu Composable):

```
app/src/main/java/com/sndiy/chatfin/
├── ai/                 # Prompt builder, integrasi Gemini, Mode Bot (fallback tanpa API key)
├── core/
│   ├── data/           # Database Room, entity, repository bersama
│   ├── di/              # Modul Hilt
│   ├── domain/          # Model & use case bersama
│   ├── notification/
│   ├── ocr/             # OCR struk (ML Kit)
│   ├── parser/           # Parsing input transaksi
│   ├── persona/          # Definisi persona Mai
│   ├── ui/               # Tema, komponen UI bersama
│   └── utils/
└── feature/
    ├── auth/
    ├── chat/
    ├── export/
    ├── finance/
    │   ├── account/       # Dompet/akun
    │   ├── analytics/
    │   ├── budget/
    │   ├── category/
    │   ├── dashboard/
    │   ├── receipt/       # Scan struk
    │   └── transaction/
    ├── onboarding/
    ├── settings/
    └── splash/
```

## Prasyarat

- [Android Studio](https://developer.android.com/studio) (versi terbaru)
- JDK 17
- File `google-services.json` dari project Firebase kamu sendiri (untuk fitur Auth/Firestore — opsional, aplikasi tetap berjalan tanpa ini untuk fitur offline)

## Menjalankan Proyek

1. Clone repository:
   ```bash
   git clone https://github.com/<username>/ChatFin.git
   cd ChatFin
   ```
2. Buka proyek di Android Studio, biarkan Gradle sync selesai.
3. (Opsional) Tambahkan `app/google-services.json` sendiri jika ingin fitur Firebase Auth/Firestore aktif.
4. (Opsional) Jalankan aplikasi lalu masukkan API key Gemini lewat halaman Pengaturan untuk mengaktifkan fitur chat AI — tanpa key, aplikasi otomatis memakai Mode Bot.
5. Jalankan aplikasi:
   ```bash
   ./gradlew installDebug
   ```

## Build dari CLI

```bash
# Build debug APK
./gradlew assembleDebug

# Jalankan unit test
./gradlew test

# Jalankan instrumentation test
./gradlew connectedAndroidTest
```

## Kontribusi

Baca [AGENTS.md](AGENTS.md) untuk aturan pengembangan (konvensi kode, aturan uang/`Long`, migrasi Room, dsb.) sebelum membuat perubahan.

## Lisensi

Didistribusikan di bawah [Lisensi MIT](LICENSE).
