package com.onikki.app.domain.grammar

/** B1 and B2 topics. */
internal object GrammarB {
    val TOPICS = listOf(
        // ------------------------------------------------------------ B1
        topic(
            "b1-perfect-vs-past", "B1", "Present Perfect vs Past Simple", "Present Perfect yoki Past Simple", "aniq o'tgan vaqt → Past Simple · vaqt muhim emas / hozirgacha → Present Perfect",
            """
            Ikkalasi ham o'tmish haqida, lekin qarash har xil.
            • Past Simple — tugagan, aniq vaqt aytilgan yoki tushunilgan: I saw him yesterday. She lived in Paris in 2019.
            • Present Perfect — vaqt aytilmagan, tajriba yoki natija hozirga bog'liq: I have seen this film. She has lived here for five years (hali ham yashaydi).
            • for / since — Present Perfect bilan: for two years (davomiylik), since 2020 (boshlanish nuqtasi).
            Savol "When…?" bo'lsa — javob Past Simple'da.
            """,
            "I have known her for ten years." to "Men uni o'n yildan beri bilaman.",
            "I met her in 2015." to "Men u bilan 2015-yilda tanishganman.",
            "Have you ever been to Japan? — Yes, I went there last summer." to "Yaponiyada bo'lganmisiz? — Ha, o'tgan yozda borgandim."
        ),
        topic(
            "b1-perfect-continuous", "B1", "Present Perfect Continuous", "Hozirgi tugallangan davomli zamon", "have/has been + fe'l-ing",
            """
            O'tmishda boshlanib, hozirgacha davom etayotgan (yoki hozirgina tugagan) ish — jarayon va davomiylik muhim.
            • I have been learning English for three years.
            • She is tired because she has been working all day.
            • How long have you been waiting?
            Holat fe'llari (know, like, want, believe) bilan ishlatilmaydi: I have known him (have been knowing emas).
            """,
            "I have been waiting for an hour." to "Men bir soatdan beri kutyapman.",
            "It has been raining since morning." to "Ertalabdan beri yomg'ir yog'yapti.",
            "What have you been doing lately?" to "So'nggi paytlarda nima bilan bandsiz?"
        ),
        topic(
            "b1-past-perfect", "B1", "Past Perfect", "O'tgan tugallangan zamon", "had + V3",
            """
            O'tmishdagi bir voqeadan ham OLDIN bo'lgan ish uchun — "o'tmishning o'tmishi".
            • When I arrived, the train had already left. (avval poyezd ketdi, keyin men keldim)
            • She was nervous because she had never flown before.
            • Ko'pincha before, after, already, by the time bilan.
            Barcha shaxslar uchun had: I had, he had, they had.
            """,
            "When we got to the cinema, the film had started." to "Kinoteatrga yetib borganimizda film boshlangan edi.",
            "I had finished my homework before dinner." to "Kechki ovqatdan oldin uy vazifamni tugatgan edim.",
            "He told me he had lost his passport." to "U pasportini yo'qotib qo'yganini aytdi."
        ),
        topic(
            "b1-first-conditional", "B1", "First Conditional", "1-shart gap (real)", "If + Present Simple, will + fe'l",
            """
            Kelajakda bo'lishi mumkin bo'lgan, real holat va uning natijasi.
            • If it rains, we will stay at home.
            • If you study hard, you will pass the exam.
            • If qismida will ishlatilmaydi: If it rains (If it will rain emas).
            • unless = if not: Unless you hurry, you'll miss the bus.
            Qismlar o'rnini almashtirsa bo'ladi: We'll stay at home if it rains.
            """,
            "If you call me, I will come." to "Qo'ng'iroq qilsangiz, kelaman.",
            "If it is sunny tomorrow, we will go to the park." to "Ertaga havo quyoshli bo'lsa, bog'ga boramiz.",
            "You won't pass unless you practise." to "Mashq qilmasangiz, o'tolmaysiz."
        ),
        topic(
            "b1-second-conditional", "B1", "Second Conditional", "2-shart gap (xayoliy)", "If + Past Simple, would + fe'l",
            """
            Hozir yoki kelajakdagi xayoliy, haqiqatdan uzoq holat — "…bo'lganida edi, …bo'lardi".
            • If I had a lot of money, I would travel around the world. (pulim yo'q)
            • If I were you, I would accept the offer. (maslahat — barcha shaxslar uchun were)
            • What would you do if you won the lottery?
            1-shartdan farqi: 1-shart — real ehtimol, 2-shart — xayol yoki kam ehtimol.
            """,
            "If I were rich, I would buy a big house." to "Boy bo'lganimda, katta uy sotib olardim.",
            "If I were you, I would talk to her." to "Sizning o'rningizda bo'lganimda, u bilan gaplashardim.",
            "What would you do if you lost your phone?" to "Telefoningizni yo'qotsangiz nima qilardingiz?"
        ),
        topic(
            "b1-passive", "B1", "Passive voice (present & past)", "Majhul nisbat", "am/is/are + V3 · was/were + V3 · (by …)",
            """
            Ishni kim qilgani emas, ishning o'zi muhim bo'lganda: "qilinadi, qilindi".
            • Present: English is spoken all over the world. Cars are made in this factory.
            • Past: The bridge was built in 1990. The windows were broken.
            • Kim qilgani kerak bo'lsa — by: The book was written by Abdulla Qodiriy.
            Faol gap: They clean the room every day. → Majhul: The room is cleaned every day.
            """,
            "This house was built in 1950." to "Bu uy 1950-yilda qurilgan.",
            "Rice is grown in many countries." to "Guruch ko'p mamlakatlarda yetishtiriladi.",
            "The letter was sent yesterday." to "Xat kecha yuborildi."
        ),
        topic(
            "b1-relative", "B1", "Relative clauses: who / which / that", "Aniqlovchi ergash gaplar", "inson → who · narsa → which · ikkalasi → that · where · whose",
            """
            Otni aniqlashtiruvchi qism — o'zbekchadagi "…gan" sifatdoshiga o'xshaydi.
            • who — inson: The man who lives next door is a doctor. (qo'shni yashaydigan odam)
            • which — narsa, hayvon: The phone which I bought is broken.
            • that — inson va narsa (so'zlashuvda ko'p): The book that you gave me is great.
            • where — joy: This is the café where we met. whose — egalik: The girl whose bag was stolen.
            Ega bo'lmasa, who/which/that tushib qolishi mumkin: The film (that) we watched was boring.
            """,
            "The woman who called you is my aunt." to "Sizga qo'ng'iroq qilgan ayol mening xolam.",
            "This is the house where I was born." to "Bu men tug'ilgan uy.",
            "I lost the watch that my father gave me." to "Otam bergan soatni yo'qotib qo'ydim."
        ),
        topic(
            "b1-reported", "B1", "Reported speech (statements)", "O'zlashtirma gap", "say / tell + zamon bir qadam orqaga",
            """
            Birovning gapini o'z so'zimiz bilan yetkazamiz. Asosan zamon bir qadam orqaga suriladi:
            • Present Simple → Past Simple: "I am tired." → She said (that) she was tired.
            • will → would: "I will help." → He said he would help.
            • Present Perfect / Past Simple → Past Perfect: "I have finished." → He said he had finished.
            • say — kimgaligini aytmasdan, tell — kimga: She told me (that)…
            Olmosh va vaqt so'zlari ham o'zgaradi: I → he/she, today → that day, tomorrow → the next day.
            """,
            "He said he was busy." to "U band ekanini aytdi.",
            "She told me she would call later." to "U menga keyinroq qo'ng'iroq qilishini aytdi.",
            "They said they had seen the film." to "Ular filmni ko'rganliklarini aytishdi."
        ),
        topic(
            "b1-used-to", "B1", "Used to", "Avval …ardi (odat)", "used to + fe'l · didn't use to",
            """
            O'tmishdagi, hozir endi yo'q odat yoki holat — "avval …ardim".
            • I used to play football every day (hozir o'ynamayman).
            • There used to be a cinema here.
            • Inkor: I didn't use to like coffee. So'roq: Did you use to live here?
            Farqlang: be used to + -ing — "ko'nikkan bo'lmoq": I'm used to getting up early.
            """,
            "I used to live in Bukhara." to "Men avval Buxoroda yashardim.",
            "She didn't use to wear glasses." to "U avval ko'zoynak taqmasdi.",
            "Did you use to play the piano?" to "Avval pianino chalarmidingiz?"
        ),
        topic(
            "b1-might-may", "B1", "Might / may / could (possibility)", "Ehtimol", "might / may / could + fe'l",
            """
            Aniq emas, ehtimoli bor ishlar uchun — "…shi mumkin, balki".
            • It might rain later. She may be at home. He could be right.
            • Inkor: might not / may not — I might not come tonight.
            • will — ishonch, might — ishonch kam: I will come (aniq) / I might come (balki).
            Bu ma'noda can ishlatilmaydi: It can rain tomorrow — noto'g'ri, It might rain tomorrow — to'g'ri.
            """,
            "Take an umbrella. It might rain." to "Soyabon oling. Yomg'ir yog'ishi mumkin.",
            "She may not know the answer." to "U javobni bilmasligi mumkin.",
            "That could be the reason." to "Sababi shu bo'lishi mumkin."
        ),
        topic(
            "b1-gerund-infinitive", "B1", "Stop / remember / try + -ing or to", "Ma'nosi o'zgaradigan fe'llar", "stop doing ≠ stop to do · remember doing ≠ remember to do",
            """
            Ba'zi fe'llarda -ing yoki to tanlovi ma'noni o'zgartiradi.
            • stop smoking — chekishni tashladi; stop to smoke — chekish uchun to'xtadi.
            • remember doing — qilganini eslaydi (o'tmish); remember to do — qilishni unutmaydi (kelajak).
            • forget to lock — qulflashni unutdi; I'll never forget meeting you — tanishganimizni hech unutmayman.
            • try doing — sinab ko'rmoq; try to do — harakat qilmoq.
            """,
            "Please remember to buy bread." to "Iltimos, non olishni unutmang.",
            "I remember visiting this place as a child." to "Bolaligimda bu yerga kelganimni eslayman.",
            "He stopped to talk to his friend." to "U do'sti bilan gaplashish uchun to'xtadi."
        ),
        topic(
            "b1-question-tags", "B1", "Question tags", "Tasdiq so'roqlari", "darak (+) → inkor teg (-) · darak (-) → ijobiy teg (+)",
            """
            Gap oxiridagi kichik savol — "…, shunday emasmi?". Tasdiq kutilganda ishlatiladi.
            • Ijobiy gap → inkor teg: You are a student, aren't you? She likes tea, doesn't she?
            • Inkor gap → ijobiy teg: He isn't here, is he? They didn't come, did they?
            • Yordamchi fe'l takrorlanadi: can → can't, will → won't, have → haven't.
            Istisno: I am → aren't I? Let's → shall we?
            """,
            "It's a beautiful day, isn't it?" to "Ajoyib kun, shunday emasmi?",
            "You don't eat meat, do you?" to "Siz go'sht yemaysiz, to'g'rimi?",
            "She has finished, hasn't she?" to "U tugatdi, shunday emasmi?"
        ),
        // ------------------------------------------------------------ B2
        topic(
            "b2-third-conditional", "B2", "Third Conditional", "3-shart gap (o'tmishdagi afsus)", "If + had + V3, would have + V3",
            """
            O'tmishda bo'lmagan holat va uning bo'lmagan natijasi — afsus yoki tanqid.
            • If I had studied harder, I would have passed the exam. (o'qimadim → o'tmadim)
            • If you had told me, I would have helped you.
            • Inkor: If she hadn't missed the bus, she wouldn't have been late.
            Qisqartma: I'd have = I would have; If I'd known = If I had known.
            """,
            "If I had known, I would have come earlier." to "Bilganimda, ertaroq kelgan bo'lardim.",
            "If we had left on time, we wouldn't have missed the flight." to "O'z vaqtida chiqqanimizda, reysdan qolmagan bo'lardik.",
            "She would have won if she had trained more." to "Ko'proq mashq qilganida, u g'alaba qozongan bo'lardi."
        ),
        topic(
            "b2-mixed-conditionals", "B2", "Mixed conditionals", "Aralash shart gaplar", "If + had V3, would + fe'l (hozir) · If + Past, would have V3",
            """
            Shart va natija har xil zamonda bo'ladi.
            • O'tmishdagi sabab → hozirgi natija: If I had taken that job, I would be rich now.
            • Hozirgi holat → o'tmishdagi natija: If I were braver, I would have said something.
            Gapdagi vaqt so'zlariga (now, yesterday) qarab qaysi qism qaysi zamonda ekanini aniqlang.
            """,
            "If I had slept well, I wouldn't be so tired now." to "Yaxshi uxlaganimda, hozir bunchalik charchamagan bo'lardim.",
            "If she spoke English, she would have got the job." to "U inglizcha gapirganida, ishga olingan bo'lardi.",
            "If we had saved money, we could buy a house now." to "Pul yiqqanimizda, hozir uy sotib olardik."
        ),
        topic(
            "b2-passive-advanced", "B2", "Passive: all tenses", "Majhul nisbat: barcha zamonlar", "be (kerakli zamonda) + V3",
            """
            "be" kerakli zamonga qo'yiladi, asosiy fe'l doim V3.
            • is being built (hozir), has been sold (Present Perfect), will be announced (kelasi).
            • had been stolen (Past Perfect), must be done (modal), can be seen.
            • Rasmiy uslub: It is said that… / He is believed to be…
            The road is being repaired. The results will be announced tomorrow. The car had been stolen before we arrived.
            """,
            "The new bridge is being built now." to "Yangi ko'prik hozir qurilmoqda.",
            "All the tickets have been sold." to "Barcha chiptalar sotib bo'lingan.",
            "The work must be finished by Friday." to "Ish jumagacha tugatilishi kerak."
        ),
        topic(
            "b2-causative", "B2", "Have / get something done", "Ishni boshqaga qildirish", "have / get + narsa + V3",
            """
            Ishni o'zimiz emas, boshqa odam (usta, xizmat) qilganda.
            • I had my hair cut yesterday. (sartarosh kesdi)
            • We are getting our flat painted.
            • She has her car serviced every year.
            Solishtiring: I cut my hair — o'zim kesdim; I had my hair cut — kestirdim.
            Salbiy hodisada ham: He had his phone stolen.
            """,
            "I need to get my phone repaired." to "Telefonimni tuzattirishim kerak.",
            "We had the house cleaned before the party." to "Ziyofatdan oldin uyni tozalatdik.",
            "She had her bag stolen on the bus." to "Avtobusda uning sumkasini o'g'irlab ketishdi."
        ),
        topic(
            "b2-reported-questions", "B2", "Reported questions & requests", "O'zlashtirma so'roq va buyruq", "asked if / wh- + darak tartib · told / asked + to + fe'l",
            """
            So'roqni yetkazganda so'z tartibi darak gapdagidek bo'ladi, do/does/did yo'qoladi.
            • Ha/yo'q savol → if / whether: "Are you tired?" → She asked if I was tired.
            • So'roq so'zli: "Where do you live?" → He asked where I lived (where did I live emas).
            • Buyruq va iltimos → to + fe'l: "Close the door." → She told me to close the door. "Don't be late." → He asked us not to be late.
            """,
            "She asked me where I worked." to "U mendan qayerda ishlashimni so'radi.",
            "He asked if I could help him." to "U unga yordam bera olishimni so'radi.",
            "The teacher told us not to talk." to "O'qituvchi bizga gaplashmaslikni aytdi."
        ),
        topic(
            "b2-future-perfect", "B2", "Future Continuous & Future Perfect", "Kelasi davomli va tugallangan zamon", "will be + -ing · will have + V3",
            """
            • Future Continuous — kelajakdagi ma'lum paytda davom etayotgan ish: This time tomorrow I will be flying to Dubai.
            • Future Perfect — kelajakdagi ma'lum vaqtgacha tugagan bo'ladigan ish: By 2030 I will have finished university.
            • by + vaqt — "…gacha" Future Perfect bilan ko'p keladi: By Friday she will have read the book.
            """,
            "At 8 pm I will be watching the match." to "Kechqurun soat sakkizda men o'yinni tomosha qilayotgan bo'laman.",
            "By next year, I will have saved enough money." to "Kelasi yilgacha yetarlicha pul yig'ib bo'laman.",
            "Will you be using the car tonight?" to "Bugun kechqurun mashinadan foydalanasizmi?"
        ),
        topic(
            "b2-wish", "B2", "Wish / If only", "Afsus va istak", "wish + Past (hozir) · wish + had V3 (o'tmish) · wish + would",
            """
            • Hozirgi holatdan norozilik: I wish I had more time. I wish I were taller.
            • O'tmishdagi afsus: I wish I had studied harder. If only I hadn't said that!
            • Boshqaning xatti-harakatidan norozilik: I wish you would stop talking.
            If only — wish dan kuchliroq hissiyot bildiradi.
            """,
            "I wish I knew the answer." to "Qani endi javobni bilsam.",
            "I wish I hadn't eaten so much." to "Shuncha ko'p yemaganimda edi.",
            "If only it would stop raining!" to "Qani endi yomg'ir to'xtasa!"
        ),
        topic(
            "b2-past-deduction", "B2", "Must have / can't have / might have", "O'tmish haqida xulosa", "must / might / can't + have + V3",
            """
            O'tmishda nima bo'lganini taxmin qilish:
            • must have — deyarli aniq (ha): The ground is wet. It must have rained.
            • can't / couldn't have — deyarli aniq (yo'q): She can't have seen me, she was asleep.
            • might / may / could have — ehtimol: He might have missed the bus.
            should have — afsus yoki tanqid: You should have told me.
            """,
            "He must have forgotten our meeting." to "U uchrashuvimizni unutgan bo'lsa kerak.",
            "She can't have finished already." to "U allaqachon tugatgan bo'lishi mumkin emas.",
            "They might have got lost." to "Ular adashib qolgan bo'lishi mumkin."
        ),
        topic(
            "b2-non-defining", "B2", "Non-defining relative clauses", "Qo'shimcha ma'lumot beruvchi ergash gaplar", ", who … , · , which … , (that ishlatilmaydi)",
            """
            Otni aniqlamaydi, unga qo'shimcha ma'lumot beradi; vergul bilan ajratiladi.
            • My brother, who lives in Seoul, is an engineer. (akam bitta, Seulda yashashi — qo'shimcha)
            • Tashkent, which is the capital, has a metro.
            • Bu turda that ishlatilmaydi va who/which tushib qolmaydi.
            • which butun gapga ham ishora qiladi: He passed the exam, which surprised everyone.
            """,
            "My mother, who is a teacher, loves books." to "O'qituvchi bo'lgan onam kitobni yaxshi ko'radi.",
            "Samarkand, which is very old, attracts many tourists." to "Juda qadimiy Samarqand ko'plab sayyohlarni jalb qiladi.",
            "She didn't call, which made me worried." to "U qo'ng'iroq qilmadi, bu meni xavotirga soldi."
        ),
        topic(
            "b2-linking", "B2", "Although / despite / however", "Zid ma'noli bog'lovchilar", "although + gap · despite + ot / -ing · However, …",
            """
            Qarama-qarshi fikrlarni bog'lash:
            • although / even though + to'liq gap: Although it was cold, we went out.
            • despite / in spite of + ot yoki -ing: Despite the rain, we went out. Despite being tired, she kept working.
            • however — yangi gap boshida, verguldan keyin: It was cold. However, we went out.
            • whereas / while — ikki narsani solishtirish: I like tea, whereas my sister prefers coffee.
            """,
            "Although he was ill, he went to work." to "Kasal bo'lsa ham, u ishga bordi.",
            "Despite the traffic, we arrived on time." to "Tirbandlikka qaramay, o'z vaqtida yetib keldik.",
            "The hotel was expensive. However, it was worth it." to "Mehmonxona qimmat edi. Biroq bunga arzirdi."
        ),
        topic(
            "b2-would-rather", "B2", "Would rather / had better", "Afzal ko'rish va maslahat", "would rather + fe'l (than) · had better + fe'l",
            """
            • would rather — afzal ko'rmoq: I'd rather stay at home than go out. I'd rather not talk about it.
            • would rather + boshqa odam + Past: I'd rather you didn't smoke here.
            • had better — kuchli maslahat, aks holda yomon natija: You'd better leave now or you'll miss the train.
            • Inkor: You'd better not be late.
            Ikkalasidan keyin ham "to" yo'q: I'd rather go (I'd rather to go emas).
            """,
            "I'd rather walk than take a taxi." to "Taksiga o'tirgandan ko'ra piyoda yurganim yaxshi.",
            "You'd better take a coat. It's cold." to "Palto olganingiz ma'qul. Sovuq.",
            "I'd rather you told me the truth." to "Menga haqiqatni aytganingizni afzal ko'raman."
        ),
        topic(
            "b2-participle", "B2", "Participle clauses", "Sifatdoshli qisqa gaplar", "-ing (faol) · V3 (majhul) · Having + V3 (avvalroq)",
            """
            Ikki gapni qisqartirib, rasmiyroq va ixcham qilish uchun (ega bir xil bo'lsa).
            • -ing — faol, bir vaqtda: Walking home, I met an old friend. (uyga ketayotib)
            • Having + V3 — birinchi ish oldin tugagan: Having finished the work, she went home.
            • V3 — majhul ma'no: Built in 1900, the house needs repairs. (1900-yilda qurilgan uy)
            Ega bir xil bo'lishi shart: Walking home, it started to rain — noto'g'ri.
            """,
            "Feeling tired, he went to bed early." to "Charchaganini sezib, u erta yotdi.",
            "Having lost my keys, I couldn't get in." to "Kalitlarimni yo'qotib qo'yganim uchun ichkariga kira olmadim.",
            "Written in 1926, the novel is still popular." to "1926-yilda yozilgan bu roman hali ham mashhur."
        )
    )
}
