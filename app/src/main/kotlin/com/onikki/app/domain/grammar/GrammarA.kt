package com.onikki.app.domain.grammar

/** A1 and A2 topics. */
internal object GrammarA {
    val TOPICS = listOf(
        // ------------------------------------------------------------ A1
        topic(
            "a1-be", "A1", "To be: am / is / are", "Bo'lmoq fe'li", "I am · he/she/it is · you/we/they are",
            """
            "To be" — "bo'lmoq" degani. O'zbek tilida "men talabaman" deymiz, ingliz tilida esa fe'l albatta kerak: I am a student.
            • I → am
            • he, she, it → is
            • you, we, they → are
            Inkor: not qo'shiladi — I am not, she is not (isn't), they are not (aren't).
            So'roq: fe'l oldinga chiqadi — Are you ready? Is he at home?
            """,
            "I am a student." to "Men talabaman.",
            "She isn't at home." to "U uyda emas.",
            "Are they your friends?" to "Ular sizning do'stlaringizmi?"
        ),
        topic(
            "a1-present-simple", "A1", "Present Simple", "Hozirgi oddiy zamon", "I/you/we/they work · he/she/it works",
            """
            Odat, doimiy holat va har doim bo'ladigan ishlar uchun: har kuni, odatda, hech qachon.
            • he, she, it bilan fe'lga -s qo'shiladi: work → works, go → goes, study → studies.
            • Inkor: don't / doesn't + fe'l (doesn't dan keyin -s yo'q): She doesn't like tea.
            • So'roq: Do / Does + ega + fe'l: Do you play football? Does he work here?
            Ko'p ishlatiladigan so'zlar: always, usually, often, sometimes, never, every day.
            """,
            "I drink tea every morning." to "Men har kuni ertalab choy ichaman.",
            "He works in a bank." to "U bankda ishlaydi.",
            "Does she speak English?" to "U inglizcha gapiradimi?"
        ),
        topic(
            "a1-present-continuous", "A1", "Present Continuous", "Hozirgi davomli zamon", "am/is/are + fe'l-ing",
            """
            Hozir, aynan shu paytda bo'layotgan ish uchun. O'zbekchadagi "-yapti" ga o'xshaydi.
            • I am reading. She is cooking. They are playing.
            • -ing qo'shish: make → making (e tushadi), run → running (undosh ikkilanadi).
            • Inkor: I'm not working. So'roq: Are you listening?
            Belgi so'zlar: now, right now, at the moment, Look!, Listen!
            """,
            "I am reading a book now." to "Men hozir kitob o'qiyapman.",
            "Look! It is raining." to "Qarang! Yomg'ir yog'yapti.",
            "What are you doing?" to "Nima qilyapsiz?"
        ),
        topic(
            "a1-articles", "A1", "Articles: a / an / the", "Artikllar", "a + undosh · an + unli · the = aniq narsa",
            """
            O'zbek tilida artikl yo'q, shuning uchun bu mavzu ko'p xato qilinadi.
            • a / an — birinchi marta tilga olingan, "bitta, qandaydir" narsa: a book, a car.
            • an — unli tovush bilan boshlangan so'zdan oldin: an apple, an hour (h o'qilmaydi).
            • the — ikkalamiz biladigan, aniq narsa: the sun, the book on the table.
            • Ko'plikda va sanalmaydigan otlarda "a" ishlatilmaydi: I like music. Cats are cute.
            """,
            "I have a cat. The cat is black." to "Mening mushugim bor. Mushuk qora.",
            "She eats an apple every day." to "U har kuni bitta olma yeydi.",
            "Close the door, please." to "Iltimos, eshikni yoping."
        ),
        topic(
            "a1-plurals", "A1", "Plural nouns", "Otlarning ko'plik shakli", "ot + s / es · man → men",
            """
            Ko'plik odatda -s bilan yasaladi: book → books.
            • -s, -sh, -ch, -x, -o bilan tugasa: -es (bus → buses, box → boxes).
            • undosh + y: y → ies (city → cities), lekin day → days.
            • -f / -fe: knife → knives, leaf → leaves.
            • Noto'g'ri: man → men, woman → women, child → children, foot → feet, tooth → teeth, person → people.
            Raqamdan keyin ham ko'plik shart: two books (o'zbekchadagidek "ikki kitob" emas).
            """,
            "I have two brothers." to "Mening ikki akam bor.",
            "There are three children in the park." to "Bog'da uchta bola bor.",
            "These boxes are heavy." to "Bu qutilar og'ir."
        ),
        topic(
            "a1-there-is", "A1", "There is / There are", "Bor (joyda)", "There is + birlik · There are + ko'plik",
            """
            Biror joyda nimadir "bor" ekanini aytish uchun.
            • There is a bank near my house. (bitta)
            • There are two banks near my house. (ko'p)
            • Inkor: There isn't any milk. There aren't any chairs.
            • So'roq: Is there a cafe here? Are there any shops?
            Diqqat: "egalik" uchun have ishlatiladi (I have a car), joyda bor bo'lish uchun there is.
            """,
            "There is a park near my school." to "Maktabim yonida bog' bor.",
            "There are five people in my family." to "Oilamda besh kishi bor.",
            "Is there a pharmacy here?" to "Bu yerda dorixona bormi?"
        ),
        topic(
            "a1-possessives", "A1", "My, your… and 's", "Egalik", "my · your · his · her · its · our · their · Ali's",
            """
            Kimga tegishli ekanini ko'rsatadi, otdan oldin keladi.
            • I → my, you → your, he → his, she → her, it → its, we → our, they → their.
            • Insonga tegishli: ism + 's — Ali's phone, my mother's car.
            • Ko'plikdagi -s bilan tugagan ot: the students' books.
            Diqqat: its (egalik) va it's (it is) har xil.
            """,
            "This is my brother. His name is Sardor." to "Bu mening akam. Uning ismi Sardor.",
            "Where is your bag?" to "Sumkangiz qayerda?",
            "That is Malika's book." to "Bu Malikaning kitobi."
        ),
        topic(
            "a1-can", "A1", "Can / can't", "Qila olmoq", "can + fe'l (o'zgarmaydi)",
            """
            Qobiliyat, imkoniyat va ruxsat uchun.
            • I can swim. She can speak three languages. (he/she bilan ham -s yo'q!)
            • Inkor: can't (cannot): I can't drive.
            • So'roq: Can you help me? — Yes, I can. / No, I can't.
            • Iltimos: Can I open the window?
            can dan keyin "to" qo'yilmaydi: can swim (can to swim emas).
            """,
            "I can play the piano." to "Men pianino chala olaman.",
            "He can't come today." to "U bugun kela olmaydi.",
            "Can you help me, please?" to "Menga yordam bera olasizmi?"
        ),
        topic(
            "a1-prep-place", "A1", "Prepositions of place", "Joy predloglari", "in · on · under · next to · between · behind",
            """
            Narsa qayerda turganini ko'rsatadi. O'zbekchada qo'shimcha bilan aytamiz (-da, ustida), inglizchada so'z oldidan predlog.
            • in — ichida: in the box, in Tashkent.
            • on — ustida: on the table, on the wall.
            • under — tagida: under the bed.
            • next to — yonida, between — o'rtasida, behind — orqasida, in front of — oldida.
            """,
            "The keys are on the table." to "Kalitlar stol ustida.",
            "The cat is under the bed." to "Mushuk karavot tagida.",
            "The bank is next to the school." to "Bank maktab yonida."
        ),
        topic(
            "a1-prep-time", "A1", "At / on / in (time)", "Vaqt predloglari", "at + soat · on + kun · in + oy/yil",
            """
            Vaqtni aytishda uchta predlog ishlatiladi:
            • at — aniq vaqt: at 7 o'clock, at night, at the weekend.
            • on — kun va sana: on Monday, on 5 May, on my birthday.
            • in — oy, yil, fasl, kun qismi: in June, in 2025, in winter, in the morning.
            Every, next, last, this oldidan predlog qo'yilmaydi: every day, next week.
            """,
            "The lesson starts at nine." to "Dars soat to'qqizda boshlanadi.",
            "I was born in 2008." to "Men 2008-yilda tug'ilganman.",
            "We meet on Fridays." to "Biz jumalari uchrashamiz."
        ),
        topic(
            "a1-have-got", "A1", "Have / has (got)", "Ega bo'lmoq", "I/you/we/they have · he/she/it has",
            """
            "Mening … bor" degan ma'noni beradi.
            • I have a car. She has two sisters.
            • Britancha shakli: I have got / she has got.
            • Inkor: I don't have a bike. He doesn't have time.
            • So'roq: Do you have a pen? Does she have a phone?
            he/she/it bilan har doim has (have emas).
            """,
            "I have a new phone." to "Mening yangi telefonim bor.",
            "She has long hair." to "Uning sochlari uzun.",
            "Do you have any brothers?" to "Akalaringiz bormi?"
        ),
        topic(
            "a1-questions", "A1", "Question words", "So'roq so'zlari", "What · Where · When · Who · Why · How",
            """
            So'roq so'zi gap boshida keladi, keyin yordamchi fe'l, keyin ega.
            • What — nima, Where — qayerda, When — qachon, Who — kim, Why — nega, How — qanday.
            • How much — qancha (narx), How many — nechta, How old — necha yoshda.
            • Where do you live? What is your name? Why are you late?
            Tartib: So'roq so'zi + do/does/am/is/are + ega + fe'l.
            """,
            "Where do you live?" to "Qayerda yashaysiz?",
            "How many students are there?" to "Nechta talaba bor?",
            "Why is she sad?" to "U nega xafa?"
        ),
        // ------------------------------------------------------------ A2
        topic(
            "a2-past-simple", "A2", "Past Simple", "O'tgan oddiy zamon", "fe'l + ed · noto'g'ri fe'llar: V2",
            """
            O'tmishda boshlanib tugagan ish uchun: kecha, o'tgan hafta, 2 yil oldin.
            • To'g'ri fe'llar: -ed — work → worked, live → lived, study → studied, stop → stopped.
            • Noto'g'ri fe'llar o'z shakliga ega (V2): go → went, see → saw, buy → bought.
            • Barcha shaxslar uchun bir xil: I went, she went, they went.
            Belgi so'zlar: yesterday, last week, ago, in 2020.
            """,
            "I watched a film yesterday." to "Kecha men film ko'rdim.",
            "We went to Samarkand last year." to "O'tgan yili Samarqandga bordik.",
            "She called me two hours ago." to "U menga ikki soat oldin qo'ng'iroq qildi."
        ),
        topic(
            "a2-past-questions", "A2", "Past Simple: questions & negatives", "O'tgan zamon: so'roq va inkor", "did / didn't + fe'l (1-shakl)",
            """
            O'tgan zamonda so'roq va inkor uchun did ishlatiladi, asosiy fe'l esa 1-shaklga qaytadi.
            • Inkor: I didn't go (didn't went emas!). She didn't call.
            • So'roq: Did you see him? Where did they live?
            • Qisqa javob: Yes, I did. / No, I didn't.
            "to be" bilan did kerak emas: Were you at home? I wasn't tired.
            """,
            "I didn't understand the question." to "Men savolni tushunmadim.",
            "Did you finish your homework?" to "Uy vazifangizni tugatdingizmi?",
            "Where did you buy that bag?" to "U sumkani qayerdan sotib oldingiz?"
        ),
        topic(
            "a2-past-continuous", "A2", "Past Continuous", "O'tgan davomli zamon", "was/were + fe'l-ing",
            """
            O'tmishda ma'lum bir paytda davom etayotgan ish uchun.
            • At 8 pm I was watching TV. They were sleeping.
            • Ko'pincha Past Simple bilan: davomli ish + uni bo'lgan qisqa voqea.
              I was cooking when the phone rang.
            • while — "…ayotganda": While I was walking, it started to rain.
            """,
            "I was sleeping when you called." to "Siz qo'ng'iroq qilganingizda men uxlayotgan edim.",
            "What were you doing at 9 o'clock?" to "Soat to'qqizda nima qilayotgan edingiz?",
            "They were playing football all afternoon." to "Ular tushdan keyin bo'yi futbol o'ynashdi."
        ),
        topic(
            "a2-will-going-to", "A2", "Will / be going to", "Kelasi zamon", "will + fe'l · am/is/are going to + fe'l",
            """
            Ikkalasi ham kelajak haqida, lekin farqi bor:
            • be going to — oldindan qilingan reja yoki ko'rinib turgan natija:
              I'm going to visit my grandmother on Sunday. Look at the clouds — it's going to rain.
            • will — shu zahoti qilingan qaror, va'da, taxmin:
              I'm tired. I'll go to bed. I will help you. I think she will win.
            Inkor: won't (will not), I'm not going to.
            """,
            "I'm going to study medicine." to "Men tibbiyotni o'qimoqchiman.",
            "Don't worry, I will call you." to "Xavotir olmang, sizga qo'ng'iroq qilaman.",
            "I think it will be sunny tomorrow." to "Menimcha ertaga havo quyoshli bo'ladi."
        ),
        topic(
            "a2-comparatives", "A2", "Comparatives & superlatives", "Qiyosiy va orttirma daraja", "-er than · the -est · more / the most",
            """
            Narsalarni solishtirish uchun.
            • Qisqa sifat: cheap → cheaper than → the cheapest; big → bigger → the biggest.
            • -y bilan: easy → easier → the easiest.
            • Uzun sifat: expensive → more expensive than → the most expensive.
            • Noto'g'ri: good → better → the best, bad → worse → the worst, far → further → the furthest.
            Teng: as … as — She is as tall as her brother.
            """,
            "My phone is cheaper than yours." to "Mening telefonim siznikidan arzonroq.",
            "This is the most beautiful city in the world." to "Bu dunyodagi eng go'zal shahar.",
            "Today is better than yesterday." to "Bugun kechagidan yaxshiroq."
        ),
        topic(
            "a2-countable", "A2", "Countable & uncountable: some, any, much, many", "Sanaladigan va sanalmaydigan otlar", "many + ko'plik · much + sanalmaydigan · some / any",
            """
            Sanaladigan otlar (apple, book) ko'plikka ega; sanalmaydiganlar (water, money, advice, information) yo'q.
            • some — darak gapda: I have some money. There are some eggs.
            • any — inkor va so'roqda: I don't have any money. Are there any eggs?
            • many — sanaladigan: How many books? much — sanalmaydigan: How much water?
            • a lot of — ikkalasiga ham: a lot of friends, a lot of time.
            Sanalmaydigan otga a/an va -s qo'shilmaydi: some advice (an advice emas).
            """,
            "There isn't much milk in the fridge." to "Muzlatgichda sut ko'p emas.",
            "How many languages do you speak?" to "Nechta tilda gapirasiz?",
            "Can you give me some advice?" to "Menga biroz maslahat bera olasizmi?"
        ),
        topic(
            "a2-present-perfect", "A2", "Present Perfect", "Hozirgi tugallangan zamon", "have/has + V3",
            """
            O'tmishda bo'lgan, lekin vaqti muhim bo'lmagan yoki natijasi hozir ham sezilayotgan ish.
            • Hayot tajribasi: I have been to Istanbul. Have you ever eaten sushi?
            • Hozirgi natija: I have lost my keys (hozir kalit yo'q).
            • ever, never, already, yet, just bilan ko'p keladi: She has just arrived. I haven't finished yet.
            • he/she/it bilan has: He has seen this film.
            Aniq o'tgan vaqt (yesterday, in 2020) bo'lsa — Past Simple ishlatiladi.
            """,
            "I have never been to London." to "Men hech qachon Londonda bo'lmaganman.",
            "Have you finished your work yet?" to "Ishingizni tugatdingizmi?",
            "She has just left." to "U hozirgina ketdi."
        ),
        topic(
            "a2-frequency", "A2", "Adverbs of frequency", "Takrorlanish ravishlari", "always · usually · often · sometimes · rarely · never",
            """
            Ish qanchalik tez-tez bo'lishini ko'rsatadi.
            • 100% always → usually → often → sometimes → rarely → never 0%.
            • Joyi: asosiy fe'ldan oldin — I often go to the gym. She never eats meat.
            • "to be" dan keyin — He is always late. They are usually busy.
            So'roq: How often do you …? — Javob: once a week, twice a month, every day.
            """,
            "I usually get up at seven." to "Men odatda soat yettida turaman.",
            "She is never late." to "U hech qachon kechikmaydi.",
            "How often do you go swimming?" to "Qanchalik tez-tez suzishga borasiz?"
        ),
        topic(
            "a2-must-have-to", "A2", "Must / have to", "Majburiyat", "must + fe'l · have to / has to + fe'l",
            """
            Majburiyatni bildiradi — "kerak, shart".
            • must — gapiruvchining o'zi zarur deb hisoblagani, qoidalar: I must study harder. You must wear a seatbelt.
            • have to — tashqi majburiyat: I have to work on Saturdays. She has to wear a uniform.
            • mustn't — taqiq: You mustn't smoke here.
            • don't have to — shart emas: You don't have to come (xohlasangiz kelasiz).
            O'tmishda faqat had to: I had to wait an hour.
            """,
            "You must be careful." to "Ehtiyot bo'lishingiz kerak.",
            "I have to get up early tomorrow." to "Ertaga erta turishim kerak.",
            "You don't have to pay, it's free." to "To'lashingiz shart emas, bu bepul."
        ),
        topic(
            "a2-should", "A2", "Should / shouldn't", "Maslahat", "should + fe'l",
            """
            Maslahat berish yoki fikr bildirish — "…sa yaxshi bo'ladi".
            • You should see a doctor. You shouldn't eat so much sugar.
            • So'roq: Should I call her? What should I do?
            • I think you should … — yumshoq maslahat.
            should dan keyin "to" yo'q va he/she bilan ham o'zgarmaydi: He should rest.
            """,
            "You should drink more water." to "Ko'proq suv ichishingiz kerak.",
            "You shouldn't go to bed so late." to "Bunchalik kech yotmasligingiz kerak.",
            "What should I wear tonight?" to "Bugun kechqurun nima kiysam bo'ladi?"
        ),
        topic(
            "a2-ing-to", "A2", "Like doing / want to do", "Fe'l + -ing yoki to + fe'l", "like / enjoy + -ing · want / decide + to",
            """
            Ba'zi fe'llardan keyin ikkinchi fe'l -ing bilan, ba'zilaridan keyin to bilan keladi.
            • -ing: like, love, hate, enjoy, finish, mind, stop — I enjoy reading. She finished cooking.
            • to + fe'l: want, need, decide, hope, plan, learn, would like — I want to travel. We decided to stay.
            • Predlogdan keyin har doim -ing: I'm good at drawing. Thank you for helping me.
            """,
            "I enjoy listening to music." to "Men musiqa tinglashni yaxshi ko'raman.",
            "She wants to become a doctor." to "U shifokor bo'lishni xohlaydi.",
            "Thank you for coming." to "Kelganingiz uchun rahmat."
        ),
        topic(
            "a2-too-enough", "A2", "Too / enough", "Juda (ortiqcha) va yetarli", "too + sifat · sifat + enough · enough + ot",
            """
            • too — "haddan tashqari", odatda salbiy: This coffee is too hot (ichib bo'lmaydi).
            • enough — "yetarli": sifatdan KEYIN keladi — She is old enough to drive.
            • otdan OLDIN keladi — We don't have enough time.
            • Ko'pincha to + fe'l bilan: It's too cold to swim. He is tall enough to play basketball.
            very va too farqi: very hot — juda issiq (lekin mumkin), too hot — ortiqcha issiq (mumkin emas).
            """,
            "This bag is too heavy for me." to "Bu sumka men uchun juda og'ir.",
            "I'm not old enough to vote." to "Men ovoz berish uchun hali yosh.",
            "We have enough chairs for everyone." to "Hammaga yetadigan stul bor."
        )
    )
}
