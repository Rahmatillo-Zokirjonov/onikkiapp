# Yangi versiyani test serverga chiqarish

Sayt: **https://dastur-test.online**. Telefonga shu sahifadan o'rnatiladi.

## Qisqasi — bitta buyruq

Kompyuterda **PowerShell** oching, keyin:

```powershell
cd "C:\Users\user\Desktop\on ikki"

# 1) Avval sinov: hech narsani o'zgartirmaydi, faqat tekshiradi
powershell -ExecutionPolicy Bypass -File deploy\deploy-test.ps1 -Version 1.4.0 -DryRun

# 2) Haqiqiy chiqarish
powershell -ExecutionPolicy Bypass -File deploy\deploy-test.ps1 -Version 1.4.0 -Yangiliklar "Kundalik|Papkalar va maxfiy qaydlar"

# 3) Faqat sahifadagi "Yangi" ro'yxatini tuzatish (APK yig'ilmaydi)
powershell -ExecutionPolicy Bypass -File deploy\deploy-test.ps1 -PageOnly -Yangiliklar "Birinchi band|Ikkinchi band"
```

- `-Version` — yangi versiya raqami. Har safar oldingisidan katta bo'lishi shart: 1.3.1 → 1.4.0 → 1.4.1 …
  - Kichik tuzatish bo'lsa, oxirgi raqamni oshiring: 1.4.0 → 1.4.1.
  - Yangi imkoniyat bo'lsa, o'rtadagisini oshiring: 1.4.1 → 1.5.0.
- `-Yangiliklar` — sahifadagi "Yangi" ro'yxati. Hammasi bitta qo'shtirnoq ichida, bandlar **`|`** belgisi bilan ajratiladi (vergul emas — vergul band ichida ham bo'lishi mumkin). Berilmasa, ro'yxat o'zgarmaydi.
- Oxirida yashil **TAYYOR** yozuvi chiqsa, hammasi bo'ldi: telefonda saytni ochib yangisini o'rnating.

## Skript ichida nima bo'ladi (o'rganish uchun)

| # | Qadam | Nima uchun |
|---|-------|-----------|
| 1 | `app/build.gradle.kts` da `versionName` = yangi raqam, `versionCode` + 1 | Telefon yangi versiyani faqat `versionCode` kattaroq bo'lsa "yangilanish" deb qabul qiladi |
| 2 | `gradle assembleRelease` | Imzolangan APK yig'iladi. Kalit `C:\Users\user\Documents\on-ikki-release-key\` da, parollar `keystore.properties` da. **Bu kalitni yo'qotmang** — busiz eski ilova ustidan yangilab bo'lmaydi |
| 3 | `deploy/index.html` yangilanadi | Sahifada versiya, hajm, sana va yangiliklar to'g'ri ko'rinishi uchun |
| 4 | `scp` bilan serverga nusxa | APK `/opt/test-loyiha/onikki/on-ikki.apk` ga, sahifa `index.html` ga tushadi |
| 5 | Saytdan qayta yuklab, SHA-256 solishtiriladi | Saytdagi fayl aynan siz yig'gan fayl ekaniga ishonch hosil qilish uchun |
| 6 | `git commit` + `git push` | Qaysi versiya qachon chiqqani GitHub'da saqlanadi |

## Server haqida bilish kerak bo'lgan narsalar

- **Ulanish faqat kalit bilan** (parol bilan kirish o'chirilgan):
  ```powershell
  ssh -i $HOME\.ssh\zargarlik_vps root@179.198.192.69
  ```
  Kalit fayli `C:\Users\user\.ssh\zargarlik_vps`. Uni hech kimga bermang va hech qayerga yuklamang.
- **Fayllar joyi:** `/opt/test-loyiha/onikki/` — ichida faqat `index.html` va `on-ikki.apk`.
- **Veb-server:** nginx. Sozlamasi `/etc/nginx/sites-available/dastur-test.online` da (nusxasi shu papkada: `nginx-dastur-test.online.conf`). Fayl almashtirish uchun nginx'ni qayta ishga tushirish shart emas.
- **SSL** (https) certbot orqali avtomatik yangilanadi.

### Serverda qo'lda ko'rish (o'rganish uchun)

```bash
ssh -i ~/.ssh/zargarlik_vps root@179.198.192.69   # serverga kirish
ls -la /opt/test-loyiha/onikki                    # fayllar va sanasi
sha256sum /opt/test-loyiha/onikki/on-ikki.apk     # fayl "barmoq izi"
tail -20 /var/log/nginx/dastur-test.access.log    # kim yuklab oldi
nginx -t                                          # nginx sozlamasi to'g'rimi
exit                                              # chiqish
```

### Skriptsiz, qo'lda chiqarish (zaxira yo'l)

```powershell
cd "C:\Users\user\Desktop\on ikki"
# 1. app/build.gradle.kts da versionCode va versionName ni o'zingiz oshiring
$env:JAVA_HOME = "C:\Users\user\.jdks\jbr-21.0.11"
& "C:\Users\user\.gradle\wrapper\dists\gradle-8.14.5-bin\690y85m0j9nfaub7xoiayko8a\gradle-8.14.5\bin\gradle.bat" assembleRelease
scp -i $HOME\.ssh\zargarlik_vps app\build\outputs\apk\release\app-release.apk root@179.198.192.69:/opt/test-loyiha/onikki/on-ikki.apk
scp -i $HOME\.ssh\zargarlik_vps deploy\index.html root@179.198.192.69:/opt/test-loyiha/onikki/index.html
git add app/build.gradle.kts deploy/index.html
git commit -m "Release 1.4.0"
git push origin main
```

## Muammo chiqsa

| Xabar | Sabab va yechim |
|-------|-----------------|
| `Serverga SSH bilan ulanib bo'lmadi` | Internetni tekshiring. Kalit fayli `C:\Users\user\.ssh\zargarlik_vps` joyidami? Hostinger panelida server yoniqmi? |
| `Permission denied (publickey)` | Kalit noto'g'ri yoki serverdan o'chirilgan. Hostinger panelidagi "Browser terminal" orqali kirib, `~/.ssh/authorized_keys` ni tekshiring |
| `Build muvaffaqiyatsiz` | Kod xatosi. Versiya o'zgarishini qaytaring: `git checkout app/build.gradle.kts`, xatoni tuzating |
| `Yangi versiya ... katta bo'lishi kerak` | `-Version` ga kattaroq raqam bering |
| Telefon "Ilova o'rnatilmadi" deydi | Odatda boshqa imzo bilan yig'ilgan bo'ladi. Faqat shu skript (release kaliti) bilan yig'ing |
| `running scripts is disabled` | Buyruqni `powershell -ExecutionPolicy Bypass -File ...` ko'rinishida yozing (yuqoridagidek) |

## Xavfsizlik qoidalari (o'zgarmas)

- Shu serverdagi `/opt/zargarlik-dastur`, `zargarlik-api` (pm2) va `zargarlik` bazasiga **tegmang** — u yerda mijoz ma'lumotlari bor. `zargarlik-api` ni **ishga tushirmang**.
- Test loyihalari faqat `/opt/test-loyiha` ichida turadi.
- Root parolni bir marta chatga yozgansiz — Hostinger panelida uni **almashtiring**.
