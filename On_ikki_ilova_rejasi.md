# "On ikki" — Shaxsiy Hayot Boshqaruv Ilovasi — To'liq Reja

## 1. Loyiha haqida

**Nomi:** On ikki (ishchi nom)
**Format:** Mobil ilova (Android birinchi, keyin iOS)
**G'oya:** Notion + Todoist + moliya boshqaruvchi + AI tahlilchini bitta ilovada birlashtirib, O'zbek foydalanuvchisi turmush tarziga (din, oila, mahalliy moliya odatlari) moslashtirish.

**Maqsadli auditoriya (V1):** Shaxsiy foydalanuvchi — o'zini rejalashtirishni, moliyasini va odatlarini boshqarishni xohlaydigan 18-40 yosh oralig'idagi O'zbekiston foydalanuvchilari.
**Kelajakdagi auditoriya (V2/V3):** Ustoz-shogird va kichik jamoa/biznes foydalanuvchilari (Rahmatilloning o'quvchilar bazasi orqali tabiiy o'sish imkoniyati bor).

---

## 2. Asosiy modullar

### 2.1 Kunlik reja va odatlar
- Kunlik/haftalik/oylik reja tuzish
- Odatlar (habits) tracker — streak va bajarilish foizi bilan
- Har bir reja/odat uchun bildirishnoma sozlash

### 2.2 Bildirishnomalar tizimi
- Reja va odatlar bo'yicha eslatmalar
- Namoz vaqtlariga moslashgan "bo'sh oyna" bildirishnomalari
- Muddat yaqinlashganda avtomatik eslatma (masalan qarz qaytarish, to'lov)

### 2.3 Screen time va ilovalarni cheklash

**Statistika:**
- Har bir ilova bo'yicha kunlik/haftalik ishlatilgan vaqt ro'yxati, eng ko'p ishlatilgandan kamgacha saralangan (ikonka, nom, daqiqa va foiz bilan)
- Kunlar/haftalar bo'yicha solishtirish grafigi ("bu hafta ijtimoiy tarmoqqa o'tgan haftaga nisbatan 20% ko'p vaqt ketdi")
- Kategoriya bo'yicha guruhlash (ijtimoiy tarmoq, o'yin, video, messenger)

**"Zararli" deb belgilash va cheklash:**
- Foydalanuvchi statistika ro'yxatidan istalgan ilovani "zararli/vaqt yeydigan" deb belgilaydi
- Har bir belgilangan ilova uchun kunlik limit qo'yiladi (masalan Instagram — kuniga 30 daqiqa)
- Limitdan oshganda: avval ogohlantirish, keyin ilova ustiga bloklovchi ekran (overlay) chiqadi
- Vaqt jadvali bo'yicha cheklash — ish/o'qish soatlarida yoki namoz vaqtida avtomatik bloklanishi mumkin
- Kun yakunidagi AI tahlil bajarilmaguncha "zararli" deb belgilangan ilovalar bloklanib turishi (2.8-band bilan bog'liq)
- "Favqulodda ochish" imkoniyati — lekin qiyinlashtirilgan holda (masalan 15 soniya kutish yoki sabab yozish talab qilinadi), to'liq taqiqlash emas, ongli tanlovga undash

**Texnik cheklovlar (MVP rejalashtirishda hisobga olinishi shart):**
- Android: statistika UsageStatsManager API orqali olinadi (foydalanuvchi "Usage Access" ruxsatini qo'lda berishi kerak), bloklash uchun Accessibility Service kerak — Google Play bunday ilovalardan aniq asoslash talab qiladi
- iOS: ancha cheklangan — Apple'ning Screen Time API'lari (DeviceActivity, FamilyControls, ManagedSettings) orqali qilinadi, bu funksiyalar uchun Apple'dan alohida entitlement so'rab tasdiqlatish kerak (avtomatik berilmaydi)
- Shu sababli: **MVP'ni Android'dan boshlash strategik jihatdan to'g'riroq**, iOS versiyasi uchun keyinroq Apple'ga alohida so'rov yuboriladi

### 2.4 Moliyaviy boshqaruv
- Kirim/chiqim asosida real vaqtli balans (naqd va karta alohida, lekin umumiy balansda birlashadi)
- Xarajatlarni kategoriya bo'yicha ajratish (oziq-ovqat, transport, kommunal, kiyim, ta'lim, sog'liq, mehmon/marosim)
- Har bir kategoriya uchun oylik budjet limiti va limitga yaqinlashganda ogohlantirish
- Diagramma (pie/bar chart) orqali "qayerga ko'p ketyapti" vizualizatsiyasi
- Qarz-nasiya moduli: kimga qarzdorsiz, kim sizga qarzdor, qaytarish muddati eslatmasi
- Maqsad uchun jamg'arma (masalan noutbuk, to'y, mashina) va progress bar
- Oy/hafta/yil bo'yicha avtomatik moliyaviy hisobot va AI orqali "moliyaviy salomatlik balli"
- Kelajakda: bank SMS orqali avtomatik xarajat aniqlash, Payme/Click/Uzcard integratsiyasi, chekni skanerlab (OCR) avtomatik kiritish

### 2.5 Qaydlar va Notion-uslubidagi ma'lumotlar bazasi
- Foydalanuvchi o'zi custom jadval/database yarata oladi (masalan "Loyihalar", "Kitoblar", "Mijozlar")
- Bir xil ma'lumotni Kanban, Calendar, List, Table ko'rinishida ko'rish
- Har bir yozuvni alohida "sahifa" sifatida ochish (ichida yana matn, checklist bo'lishi mumkin)
- Tayyor shablonlar (dars rejasi, kurs dasturi va h.k.)
- Eslatmalar orasida bog'lanish (backlink) va teglar orqali qidirish — "second brain" tizimi

### 2.6 Joylashuv va vaqt tracking
- "Bugun qayerda va nechida bo'lishim kerak" rejasi
- Agar rejadagi vaqtda o'sha joyda bo'lmasa — buni ham avtomatik qayd qilish
- Kun oxirida "bugun qayerlarda bo'lganingiz" xulosasi

### 2.7 Maqsadlar va loyihalar
- Katta maqsad qo'yish va uni kichik kunlik vazifalarga bo'lib chiqish
- Loyihalarni bosqichma-bosqich rejalashtirish (timeline bilan)

### 2.8 AI kunlik tahlil
- Kun oxirida AI orqali kunning avtomatik tahlili (nima bajarildi, nima qoldi, tavsiyalar)
- Agar tahlil/kun yopilmasa — screen time blokировкаsi ishga tushishi (2.3 bilan bog'liq)

### 2.9 Gamifikatsiya
- XP va level tizimi, kunlik/haftalik streak
- "Eng samarali kuning" statistikasi
- Do'stlar bilan maqsad musobaqasi (ixtiyoriy, ijtimoiy bosim emas, o'zini rag'batlantirish uchun)

### 2.10 Journaling (kundalik)
- Matn, ovozli eslatma yoki rasm bilan kun yozuvi
- Kayfiyat (mood) belgilash
- Yil oxirida AI orqali avtomatik "yil xulosasi"

### 2.11 Fokus rejimi
- Pomodoro timer
- Fokus vaqtida bildirishnomalar o'chirilishi

### 2.12 Odamlar va jamoa boshqaruvi
- Kontaktlar va aloqa tarixi
- Kichik jamoa/oila uchun umumiy workspace (vazifa taqsimoti, umumiy kalendar/budjet)

### 2.13 O'zbek muhitiga moslashtirish (asosiy farqlovchi xususiyat)
- Namoz vaqtlari asosida kunlik reja tuzilishi
- Ramazon rejimi — saharlik/iftor/tarovihga moslashgan maxsus kunlik tartib
- Diniy va milliy bayramlar kalendari (Navro'z, hayitlar, Mustaqillik kuni)
- To'y, ma'raka, sunnat kabi marosimlarni rejalashtirish va byudjetlash moduli
- Qarindosh-urug' bilan aloqa eslatmalari (ota-onaga qo'ng'iroq, tug'ilgan kunlar)
- Lotin/Kirill alifbosi almashtirish, rus tilida interfeys
- Offline-first ishlash (internet uzilishlariga chidamli) va eski qurilmalarda ham yengil ishlashi

---

## 3. Bosqichlar (Roadmap)

**V1 — MVP (asosiy, tezroq chiqariladigan versiya)**
- Kunlik reja + odatlar + bildirishnoma
- Moliyaviy boshqaruv (qo'lda kiritish, kategoriya, balans, oddiy diagramma)
- Namoz vaqtlariga moslashgan kalendar
- Oddiy qaydlar (hali to'liq database emas)

**V2**
- Notion-uslubidagi custom database va ko'rinishlar
- AI kunlik tahlil va screen time blocking
- Gamifikatsiya (streak, XP)
- Journaling

**V3**
- Second brain (backlink, teglar)
- Jamoa/oila workspace, ustoz-shogird funksiyalari
- Bank SMS/Payme/Click integratsiyasi, chek OCR
- Shablonlar bozori (marketplace)

---

## 4. Texnik stack tavsiyasi

- **Frontend (mobil):** React Native yoki Flutter — ikkalasi ham offline-first va tez ishlab chiqishga mos
- **Backend:** NestJS + PostgreSQL + Prisma (sizning boshqa loyihalaringizda ishlatilgan stack bilan mos, tajriba bor)
- **Lokal DB (offline uchun):** SQLite yoki WatermelonDB, keyin server bilan sync
- **AI qatlam:** Claude API (kunlik tahlil, yil xulosasi, moliyaviy tavsiyalar uchun)
- **Push-bildirishnoma:** Firebase Cloud Messaging
- **To'lov/integratsiya (V3):** Payme, Click API

---

## 5. Monetizatsiya g'oyasi (keyingi bosqich uchun)

- Freemium model: asosiy reja/odat/moliya moduli bepul
- Premium (pullik obuna): AI tahlil, second brain, jamoa workspace, shablonlar
- Ustozlar/kurs egalari uchun alohida B2B tarif (agar shogird-boshqaruv funksiyasi rivojlansa)

---

## 6. Dizayn uchun prompt

Ushbu reja asosida ilova dizaynini (UI/UX mockup) yaratish uchun tayyorlangan prompt. Buni Figma AI, v0.dev, Galileo AI kabi vositalarga yoki to'g'ridan-to'g'ri Claude'ga (masalan `design` skill orqali) berish mumkin:

```
Mobil ilova uchun zamonaviy, minimalist UI/UX dizayn yarat. Ilova nomi: "On ikki" —
O'zbekiston foydalanuvchilari uchun mo'ljallangan shaxsiy hayot boshqaruv ilovasi
(Notion + Todoist + moliya boshqaruvchi + AI tahlilchi kombinatsiyasi).

Asosiy ekranlar (MVP):
1. Bosh sahifa (Dashboard) — bugungi reja, odatlar progress, moliyaviy balans
   qisqacha ko'rinishi, namoz vaqtlariga moslashgan kunlik taymlayn bitta ekranda
2. Kunlik reja ekrani — vazifalar ro'yxati, checklist, vaqt bo'yicha tartiblangan
3. Odatlar (Habits) ekrani — streak ko'rsatkichi bilan kartochkalar
4. Moliya ekrani — balans, kategoriya bo'yicha diagramma (pie chart),
   oxirgi tranzaksiyalar ro'yxati, budjet limit progress barlari
5. Qaydlar/Database ekrani — Notion uslubidagi jadval/kanban ko'rinishi
6. Kun yakuni/AI tahlil ekrani — kunlik statistika va AI tavsiyalari

Dizayn uslubi:
- Minimalist, toza, ko'p bo'sh joy (whitespace)
- Yumshoq ranglar palitrasi, asosiy rang — moviy yoki yashil tonlarda
  (ishonch va tinchlik hissi uchun), aksent rang sifatida issiq rang
- Dark mode va light mode ikkalasi ham bo'lsin
- Katta, o'qish oson shrift (Lotin va Kirill alifbosini qo'llab-quvvatlaydigan)
- Kartochka (card) asosidagi layout, yumaloq burchaklar
- Diagrammalar va progress barlar aniq va tushunarli bo'lsin
- Bottom navigation bar 4-5 asosiy bo'lim bilan: Bosh sahifa, Reja, Moliya,
  Qaydlar, Profil

Platforma: iOS va Android, mobil-first, bir qo'l bilan ishlatishga qulay.
```
