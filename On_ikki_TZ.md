# "On ikki" — Texnik Topshiriq (TZ) — MVP v1.0

## 1. Loyiha maqsadi

Shaxsiy hayot boshqaruv ilovasi: kunlik reja, odatlar, moliya va ilova cheklashni bitta joyda birlashtiruvchi, to'liq offline ishlaydigan Android ilova. Namoz vaqtlariga moslashgan, O'zbek foydalanuvchisi uchun.

**MVP qamrovi:** faqat Android, faqat lokal (server yo'q), APK sifatida to'g'ridan-to'g'ri o'rnatiladi (Play Store shart emas).

---

## 2. Nofunksional talablar

| Talab | Qiymat |
|---|---|
| Platforma | Android (native, Kotlin) |
| Minimal Android versiya | Android 8.0 (API 26)+ |
| Internet | Faqat AI tahlil moduli uchun kerak, qolgan hammasi offline |
| Ma'lumotlar saqlash | Lokal (Room/SQLite), serverga yuborilmaydi |
| Til | O'zbek (lotin), matnlar keyinchalik kirill/rus uchun kengaytiriladigan (string resource) |
| Valyuta | So'm, ming ajratgich bilan (masalan 1 250 000) |
| Distributsiya | To'g'ridan-to'g'ri APK |

---

## 3. Funksional talablar (MVP)

### 3.1 Kunlik reja
- Vazifa qo'shish: nom, vaqt, sana, kategoriya (ish/shaxsiy)
- Bajarildi/bajarilmadi belgilash (checkbox)
- Kun bo'yicha ro'yxat, haftalik sana tanlagich (Du-Ya)
- Bajarilish progressi (masalan 4/7)

### 3.2 Odatlar (Habits)
- Odat qo'shish: nom, ikonka, kunlik maqsad
- Kunlik belgilash va streak hisoblash
- Doira progress ko'rsatkichi (foizda)

### 3.3 Bildirishnomalar (lokal)
- Har bir vazifa/odat uchun mahalliy skedulланган eslatma (AlarmManager/WorkManager)
- Namoz vaqtiga bog'liq eslatmalar
- Server yoki Firebase shart emas

### 3.4 Namoz vaqtlari
- Offline hisoblash kutubxonasi asosida (masalan Adhan)
- Foydalanuvchi bir marta shahar/joylashuvni tanlaydi, keyin internetsiz hisoblanadi
- "Keyingi namozgacha" qolgan vaqt ko'rsatkichi bosh sahifada

### 3.5 Moliyaviy boshqaruv
- Kirim/chiqim qo'shish: summa, tur, kategoriya, sana, izoh
- Naqd va karta hamyonlari alohida, umumiy balans birlashtirilgan holda
- Kategoriya bo'yicha oylik limit va ogohlantirish
- Qarz-nasiya ro'yxati (kimga qarzdor / kim qarzdor, muddat)
- Jamg'arma maqsadi (target summa + progress)
- Barchasi lokal, bank integratsiyasi MVP'da yo'q

### 3.6 Ilovalar nazorati (screen time)
- Kunlik/haftalik ilova ishlatish statistikasi (UsageStatsManager)
- Ilovani "zararli" deb belgilash + kunlik limit (daqiqada)
- Limitdan oshganda bloklovchi ekran (AccessibilityService orqali)
- "15 soniya kutib ochish" favqulodda variant (friction bilan)
- Ruxsat: foydalanuvchi Sozlamalar > Maxsus ruxsatlardan qo'lda yoqadi (onboarding ekranida tushuntiriladi)

### 3.7 Kun yakuni / AI tahlil
- Kun oxirida bajarilgan/bajarilmagan vazifalar statistikasi
- **Internet bor:** Claude API orqali qisqa AI tahlil va tavsiya
- **Internet yo'q:** faqat hisoblangan statistika ko'rsatiladi (AI qismisiz), internet qaytganda AI tahlil avtomatik yuklanadi
- Shu tahlil bajarilmaguncha "zararli" ilovalar bloklanib turadi (3.6 bilan bog'liq)

### 3.8 Qaydlar (oddiy versiya)
- Oddiy matnli eslatma qo'shish, teg qo'yish
- To'liq Notion-uslubidagi database — MVP'dan tashqarida (V2)

---

## 4. Texnik arxitektura

- **Til:** Kotlin
- **DB:** Room (SQLite), barcha modullar uchun bitta lokal baza
- **Bildirishnoma:** AlarmManager / WorkManager (lokal skedulланган)
- **Namoz vaqti:** offline kutubxona (Adhan yoki muqobili)
- **Screen time / blocking:** UsageStatsManager + AccessibilityService (native Android API)
- **AI tahlil:** faqat shu modul internetga chiqadi — Claude API (HTTPS so'rov, faqat kun yakuni matnini yuboradi)
- **Server:** yo'q (V1)
- **Auth/login:** yo'q (V1)

---

## 5. Ma'lumotlar modeli (asosiy jadvallar)

| Jadval | Asosiy maydonlar |
|---|---|
| Task | id, title, date, time, category, is_completed, habit_id (null bo'lishi mumkin) |
| Habit | id, name, icon, daily_target, streak_count |
| HabitLog | id, habit_id, date, is_done |
| Transaction | id, amount, type (kirim/chiqim), category, wallet, date, note |
| CategoryBudget | id, category, monthly_limit |
| Debt | id, person_name, amount, direction, due_date, status |
| SavingsGoal | id, name, target_amount, current_amount, deadline |
| AppUsage | package_name, app_name, date, minutes_used |
| AppLimit | package_name, daily_limit_minutes, is_harmful, blocked_hours |
| DailyReview | date, completed_count, total_count, ai_summary (null bo'lishi mumkin), synced |
| Note | id, title, content, tags, created_at |

---

## 6. Ekranlar ro'yxati (MVP)

1. Onboarding — ruxsatlar tushuntirilishi (Usage Access, Accessibility, Notifications, shahar tanlash)
2. Bosh sahifa — kun salomi, namoz vaqti kartochkasi, bugungi reja qisqacha, odatlar progressi
3. Kunlik reja — to'liq kun taymlayni, vazifa qo'shish
4. Odatlar — ro'yxat va streak
5. Moliya — balans, tranzaksiyalar, diagramma, qarz-nasiya, jamg'arma
6. Ilovalar nazorati — statistika, limit belgilash, "zararli" toggle
7. Bloklangan ekran (overlay/modal)
8. Kun yakuni — statistika + AI tahlil (yoki offline xabar)
9. Qaydlar — oddiy ro'yxat
10. Sozlamalar — shahar, til, ruxsatlar holati

*(Dizayn maketlari Claude Design'da tayyor: Bosh sahifa va Kunlik reja — dark/light. Qolganlari xuddi shu dizayn tizimi asosida qo'shiladi.)*

---

## 7. Kerakli ruxsatlar (Android permissions)

- `PACKAGE_USAGE_STATS` — statistika uchun (qo'lda yoqiladi)
- `BIND_ACCESSIBILITY_SERVICE` — bloklash uchun (qo'lda yoqiladi)
- `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS` (Android 13+)
- `ACCESS_COARSE_LOCATION` — ixtiyoriy, shahar qo'lda tanlansa shart emas
- `INTERNET` — faqat AI tahlil so'rovi uchun

---

## 8. MVP qabul mezonlari

- [ ] Ilova internetsiz to'liq ishlaydi (AI tahlildan tashqari hammasi)
- [ ] Vazifa/odat qo'shish, bajarish, streak hisoblanishi ishlaydi
- [ ] Moliyaviy tranzaksiya qo'shilib, balans to'g'ri hisoblanadi
- [ ] Namoz vaqti internetsiz to'g'ri ko'rsatiladi
- [ ] Belgilangan ilova limitdan oshganda bloklanadi
- [ ] Kun yakunida statistika ko'rinadi, internet bo'lsa AI tahlil qo'shiladi
- [ ] APK to'g'ridan-to'g'ri o'rnatilib, akkauntsiz ishga tushadi

---

## 9. MVP'dan tashqarida (V2/V3)

Notion-uslubidagi to'liq database, second brain (backlink), gamifikatsiya (XP/level), journaling, oila/jamoa workspace, bank SMS/Payme/Click integratsiyasi, chek OCR, iOS versiyasi (Apple Screen Time API entitlementi tasdiqlangandan keyin), server + sync + login.
