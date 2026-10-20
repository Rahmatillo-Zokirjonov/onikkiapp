package com.onikki.app.domain.grammar

/** Built-in exercises for B1–B2 (format: see GrammarDrillsA). */
internal object GrammarDrillsB {
    val ALL: Map<String, String> = mapOf(
        "b1-perfect-vs-past" to """
C|I ___ him yesterday.|have seen;saw;have saw;see|1|yesterday → Past Simple
C|She ___ here since 2018.|lived;has lived;lives;is living|1|since → Present Perfect
G|We ___ to Japan two years ago.|go|went|ago → Past Simple
G|I ___ this book three times.|read|have read~'ve read|tajriba, vaqt aytilmagan
F|I have visited Rome last year.|I visited Rome last year.|last year → Past Simple
F|She lives here for ten years.|She has lived here for ten years.|for + hozirgacha → Present Perfect
T|"since 2020" bilan yozing|I started working here in 2020.|I have worked here since 2020.~I've worked here since 2020.~I have been working here since 2020.|since → Present Perfect
T|"last week" bilan Past Simple ga o'tkazing|I have seen that film.|I saw that film last week.|aniq vaqt → Past Simple
""",
        "b1-perfect-continuous" to """
C|I ___ for you for an hour!|wait;have been waiting;am waiting;waited|1|hozirgacha davom etayotgan jarayon
C|She is tired because she ___ all day.|works;has been working;worked;is work|1|natija + jarayon
G|It ___ since this morning.|rain|has been raining|has been + -ing
G|How long ___ you been learning English?|have|have|How long have you been…
F|I have been knowing him for years.|I have known him for years.|know — holat fe'li, davomli emas
F|They has been playing for two hours.|They have been playing for two hours.|they → have
T|"for two hours" bilan yozing|I started reading two hours ago and I'm still reading.|I have been reading for two hours.~I've been reading for two hours.|have been + -ing + for
T|So'roq qiling (How long)|She has been studying.|How long has she been studying?|How long + has + she + been + -ing
""",
        "b1-past-perfect" to """
C|When we arrived, the film ___.|already started;had already started;has already started;starts|1|oldinroq bo'lgan ish: had + V3
C|She said she ___ the book before.|read;has read;had read;reads|2|o'tmishdan oldin → had read
G|I couldn't get in because I ___ my keys.|lose|had lost|avval yo'qotdi → had lost
G|By the time he called, I ___ to bed.|go|had gone|had + gone
F|When I got home, my sister has cooked dinner.|When I got home, my sister had cooked dinner.|o'tmish ichida → had
F|He had went before I came.|He had gone before I came.|had + V3: gone
T|Bir gapga birlashtiring (had)|The train left. Then I arrived at the station.|When I arrived at the station, the train had left.~The train had left when I arrived at the station.~The train had left before I arrived at the station.|avvalgi ish → had left
T|Inkor qiling|She had seen the sea before.|She hadn't seen the sea before.~She had not seen the sea before.|hadn't + V3
""",
        "b1-first-conditional" to """
C|If it ___ tomorrow, we will stay at home.|will rain;rains;rained;raining|1|if qismida Present Simple
C|If you study, you ___ the exam.|pass;will pass;passed;would pass|1|natija: will
G|If I see him, I ___ him.|tell|will tell~'ll tell|natija: will + fe'l
G|You'll be late ___ you hurry.|if / unless|unless|unless = if not
F|If it will be sunny, we'll go out.|If it is sunny, we'll go out.~If it's sunny, we'll go out.|if qismida will yo'q
F|If you heat ice, it melt.|If you heat ice, it melts.|it → melts
T|"unless" bilan yozing|If you don't hurry, you'll miss the bus.|Unless you hurry, you'll miss the bus.~Unless you hurry, you will miss the bus.|unless = if not
T|Shart gap tuzing|Maybe I'll have time. Then I'll help you.|If I have time, I'll help you.~If I have time, I will help you.|If + Present, will
""",
        "b1-second-conditional" to """
C|If I ___ rich, I would buy a yacht.|am;were;will be;be|1|xayoliy: were
C|What ___ you do if you won the lottery?|will;would;do;did|1|2-shart: would
G|If I had more time, I ___ a new language.|learn|would learn~'d learn|would + fe'l
G|If I ___ you, I would accept the offer.|be|were|If I were you
F|If I would have a car, I would drive to work.|If I had a car, I would drive to work.|if qismida Past Simple
F|If she knew the answer, she will tell us.|If she knew the answer, she would tell us.|2-shart: would
T|Xayoliy shart gapga aylantiring|I don't have a dog because I live in a flat.|If I didn't live in a flat, I would have a dog.~If I didn't live in a flat, I'd have a dog.|If + Past, would
T|Maslahatni "If I were you" bilan yozing|You should see a doctor.|If I were you, I would see a doctor.~If I were you, I'd see a doctor.|If I were you, I would…
""",
        "b1-passive" to """
C|English ___ all over the world.|speaks;is spoken;spoke;is speak|1|majhul: is + V3
C|The Pyramids ___ thousands of years ago.|built;were built;was built;are built|1|ko'plik, o'tmish → were built
G|This bridge ___ in 1990.|build|was built|o'tmish birlik → was + V3
G|Rice ___ in many Asian countries.|grow|is grown|hozirgi majhul → is grown
F|The letter was wrote by Ali.|The letter was written by Ali.|V3: written
F|The rooms are clean every day.|The rooms are cleaned every day.|are + V3: cleaned
T|Majhulga aylantiring|They make cars in this factory.|Cars are made in this factory.|are + made
T|Majhulga aylantiring (by)|Shakespeare wrote Hamlet.|Hamlet was written by Shakespeare.|was written by
""",
        "b1-relative" to """
C|The man ___ lives next door is a doctor.|which;who;where;whose|1|inson → who
C|This is the café ___ we first met.|which;who;where;whose|2|joy → where
G|I lost the phone ___ my father gave me.|which / who|which~that|narsa → which / that
G|That's the girl ___ brother is a pilot.|who / whose|whose|egalik → whose
F|The book who I read was great.|The book which I read was great.~The book that I read was great.~The book I read was great.|narsa → which/that
F|The woman which called you is my aunt.|The woman who called you is my aunt.~The woman that called you is my aunt.|inson → who
T|Bir gapga birlashtiring (who)|I have a friend. He speaks five languages.|I have a friend who speaks five languages.~I have a friend that speaks five languages.|who + speaks
T|Bir gapga birlashtiring (where)|This is the town. I was born there.|This is the town where I was born.|where
""",
        "b1-reported" to """
C|"I am tired," she said. → She said she ___ tired.|is;was;has been;will be|1|is → was
C|"I will help you." → He said he ___ help me.|will;would;can;is|1|will → would
G|"I like pizza." → Ali said he ___ pizza.|like|liked|Present → Past
G|"I have finished." → She said she ___ finished.|have|had|have → had
F|She said me that she was busy.|She told me that she was busy.~She told me she was busy.~She said that she was busy.|kimga aytilsa → told me
F|He said that he is hungry.|He said that he was hungry.~He said he was hungry.|zamon orqaga: was
T|O'zlashtirma gapga aylantiring|"I live in Tashkent," Bobur said.|Bobur said that he lived in Tashkent.~Bobur said he lived in Tashkent.|live → lived
T|O'zlashtirma gapga aylantiring (told me)|"I can swim," she said to me.|She told me that she could swim.~She told me she could swim.|can → could
""",
        "b1-used-to" to """
C|I ___ play football every day when I was a child.|use to;used to;was used to;using to|1|o'tmishdagi odat: used to
C|___ you use to live here?|Do;Did;Were;Used|1|so'roq: Did you use to
G|There ___ be a cinema in this street.|use|used to|There used to be
G|She didn't ___ like coffee.|use to|use to|didn't + use to
F|I used to living in Bukhara.|I used to live in Bukhara.|used to + 1-shakl
F|He didn't used to smoke.|He didn't use to smoke.|didn't dan keyin use
T|"used to" bilan yozing|I played the piano as a child, but I don't now.|I used to play the piano.|used to + play
T|So'roq qiling|She used to wear glasses.|Did she use to wear glasses?|Did + use to
""",
        "b1-might-may" to """
C|Take an umbrella. It ___ rain later.|might;must;should;can|0|ehtimol → might
C|I'm not sure. She ___ be at home.|will;may;must;is|1|aniq emas → may
G|We ___ go to the beach tomorrow, if it's sunny.|might|might|balki
G|He ___ not know the answer.|may|may|may not — bilmasligi mumkin
F|It can rain tomorrow.|It might rain tomorrow.~It may rain tomorrow.~It could rain tomorrow.|kelajak ehtimoli → might/may
F|She might comes later.|She might come later.|might + 1-shakl
T|"might" bilan yozing|Perhaps he is busy.|He might be busy.|might be
T|"may not" bilan yozing|Perhaps I won't come tonight.|I may not come tonight.|may not + fe'l
""",
        "b1-gerund-infinitive" to """
C|Please remember ___ the door when you leave.|locking;to lock;lock;locked|1|kelajakda qilish → remember to
C|I remember ___ this place as a child.|visit;to visit;visiting;visited|2|o'tmishni eslash → remember -ing
G|He stopped ___ to his friend. (gaplashish uchun to'xtadi)|talk|to talk|maqsad: stop to
G|She stopped ___ last year. (chekishni tashladi)|smoke|smoking|tashladi: stop -ing
F|I forgot calling her yesterday, sorry!|I forgot to call her yesterday, sorry!|qilishni unutdi → forget to
F|Don't forget buying milk.|Don't forget to buy milk.|forget to + fe'l
T|"try" bilan yozing (sinab ko'rmoq)|Maybe drinking warm water will help.|Try drinking warm water.|try + -ing
T|"stop" bilan yozing (tashlamoq)|I don't eat sugar any more.|I stopped eating sugar.~I have stopped eating sugar.|stop + -ing
""",
        "b1-question-tags" to """
C|You are a student, ___?|are you;aren't you;isn't it;don't you|1|ijobiy → inkor teg
C|She doesn't eat meat, ___?|doesn't she;does she;is she;isn't she|1|inkor → ijobiy teg
G|They went home, ___ they?|did|didn't|ijobiy → didn't
G|He can swim, ___ he?|can|can't|can → can't
F|It's cold today, is it?|It's cold today, isn't it?|ijobiy → inkor teg
F|You haven't finished, haven't you?|You haven't finished, have you?|inkor → ijobiy
T|Teg qo'shing|She has a car.|She has a car, doesn't she?~She has a car, hasn't she?|has → doesn't she
T|Teg qo'shing|I am late.|I am late, aren't I?~I'm late, aren't I?|I am → aren't I
""",
        // ------------------------------------------------------------ B2
        "b2-third-conditional" to """
C|If I had known, I ___ you.|would help;would have helped;had helped;will help|1|3-shart: would have + V3
C|If she ___ the bus, she wouldn't have been late.|didn't miss;hadn't missed;wouldn't miss;doesn't miss|1|if + had + V3
G|If we had left earlier, we ___ the train.|catch|would have caught~'d have caught|would have + caught
G|If you ___ me, I would have come.|tell|had told~'d told|had + V3
F|If I would have studied, I would have passed.|If I had studied, I would have passed.|if qismida had + V3
F|If he had asked, I would helped him.|If he had asked, I would have helped him.|would have + V3
T|3-shart gap tuzing|I didn't see you, so I didn't say hello.|If I had seen you, I would have said hello.~If I'd seen you, I'd have said hello.|If + had V3, would have V3
T|3-shart gap tuzing|She didn't train, so she lost.|If she had trained, she wouldn't have lost.~If she had trained, she would have won.|had trained → wouldn't have lost
""",
        "b2-mixed-conditionals" to """
C|If I had taken that job, I ___ rich now.|would have been;would be;am;will be|1|o'tmish sabab → hozirgi natija: would be
C|If she spoke English, she ___ the job last year.|would get;would have got;got;had got|1|hozirgi holat → o'tmish natija
G|If I had slept well, I ___ tired now.|not be|wouldn't be~would not be|hozirgi natija: wouldn't be
G|If he were more careful, he ___ the accident yesterday.|not have|wouldn't have had~would not have had|o'tmish natija: wouldn't have + V3
F|If I had saved money, I would have a car now, would I?|If I had saved money, I would have a car now.|teg kerak emas
F|If she had studied medicine, she would have been a doctor now.|If she had studied medicine, she would be a doctor now.|now → would be
T|Aralash shart gap tuzing (now)|I didn't eat breakfast, so I'm hungry now.|If I had eaten breakfast, I wouldn't be hungry now.|had eaten → wouldn't be
T|Aralash shart gap tuzing|He isn't brave, so he didn't speak up.|If he were brave, he would have spoken up.|were → would have spoken
""",
        "b2-passive-advanced" to """
C|The road ___ at the moment.|is repaired;is being repaired;has repaired;repairs|1|hozir jarayon: is being + V3
C|All the tickets ___ already.|have sold;have been sold;were selling;are sold|1|Present Perfect majhul: have been sold
G|The results ___ tomorrow.|announce|will be announced|kelasi majhul
G|The work must ___ by Friday.|finish|be finished|modal + be + V3
F|The car had stolen before we arrived.|The car had been stolen before we arrived.|had been + V3
F|A new hospital is building in our town.|A new hospital is being built in our town.|is being built
T|Majhulga aylantiring|They have cancelled the meeting.|The meeting has been cancelled.|has been + V3
T|Majhulga aylantiring|People will remember this day.|This day will be remembered.|will be + V3
""",
        "b2-causative" to """
C|I ___ my hair cut yesterday.|did;had;made;was|1|have something done
C|We are getting our flat ___.|paint;painting;painted;to paint|2|get + narsa + V3
G|I need to get my phone ___.|repair|repaired|V3
G|She ___ her car serviced every year.|have|has|she → has
F|I had cut my hair at the barber's.|I had my hair cut at the barber's.|have + narsa + V3
F|We got the house clean before the party.|We got the house cleaned before the party.|V3: cleaned
T|"have … done" bilan yozing|A mechanic fixed my car.|I had my car fixed.|had + my car + fixed
T|"get … done" bilan yozing|Someone will deliver the furniture to us.|We will get the furniture delivered.~We'll get the furniture delivered.|get + furniture + delivered
""",
        "b2-reported-questions" to """
C|"Where do you live?" → She asked me where I ___.|live;lived;did live;do live|1|darak tartib, zamon orqaga
C|"Are you tired?" → He asked ___ I was tired.|that;if;what;do|1|ha/yo'q → if
G|"Close the door." → She told me ___ the door.|close|to close|buyruq → to + fe'l
G|"Don't be late." → He asked us ___ late.|not be|not to be|inkor buyruq → not to
F|She asked me where did I work.|She asked me where I worked.|o'zlashtirmada darak tartib
F|He told me don't touch it.|He told me not to touch it.|not to + fe'l
T|O'zlashtirmaga aylantiring|"Can you help me?" he asked.|He asked if I could help him.~He asked whether I could help him.~He asked me if I could help him.|if + could
T|O'zlashtirmaga aylantiring|"What time is it?" she asked.|She asked what time it was.|darak tartib: it was
""",
        "b2-future-perfect" to """
C|This time tomorrow I ___ to Dubai.|will fly;will be flying;will have flown;fly|1|kelajakdagi jarayon → will be + -ing
C|By 2030 I ___ university.|will finish;will be finishing;will have finished;finish|2|by + vaqt → will have + V3
G|By Friday she ___ the book.|read|will have read|will have + V3
G|At 8 pm we ___ dinner.|have|will be having|will be + -ing
F|By next year I will save enough money.|By next year I will have saved enough money.|by → will have + V3
F|Don't call at 9, I will sleep then.|Don't call at 9, I will be sleeping then.|o'sha paytda jarayon
T|Future Perfect bilan yozing (by Monday)|I'll finish the report. Deadline: Monday.|I will have finished the report by Monday.~I'll have finished the report by Monday.|will have finished by
T|Future Continuous bilan yozing|Tomorrow at 10 I have an exam (it lasts two hours).|Tomorrow at 10 I will be taking an exam.~Tomorrow at 10 I will be writing an exam.~Tomorrow at 10 I'll be taking an exam.|will be + -ing
""",
        "b2-wish" to """
C|I wish I ___ more time. (hozir)|have;had;will have;having|1|hozirgi istak → Past
C|I wish I ___ so much yesterday.|didn't eat;hadn't eaten;don't eat;wouldn't eat|1|o'tmishga afsus → had + V3
G|I wish I ___ taller.|be|were~was|wish + were
G|If only I ___ to her. (o'tmish)|listen|had listened|had + V3
F|I wish I know the answer.|I wish I knew the answer.|wish + Past
F|I wish I didn't say that yesterday.|I wish I hadn't said that yesterday.|o'tmish → hadn't said
T|"I wish" bilan yozing|I can't speak French.|I wish I could speak French.|can't → could
T|"I wish" bilan yozing (o'tmish)|I didn't go to the party.|I wish I had gone to the party.~I wish I'd gone to the party.|had gone
""",
        "b2-past-deduction" to """
C|The ground is wet. It ___ rained.|must have;can't have;should;must|0|deyarli aniq → must have
C|She ___ seen me. She was asleep.|must have;can't have;should have;might|1|imkonsiz → can't have
G|He ___ missed the bus. I'm not sure.|might|might have|ehtimol → might have
G|You ___ told me! I would have helped.|should|should have|afsus/tanqid → should have
F|He must has forgotten.|He must have forgotten.|must + have + V3
F|They can't have went home already.|They can't have gone home already.|have + V3: gone
T|"must have" bilan yozing|I'm sure she left early.|She must have left early.|must have + V3
T|"can't have" bilan yozing|I'm sure he didn't see us.|He can't have seen us.~He couldn't have seen us.|can't have + V3
""",
        "b2-non-defining" to """
C|My brother, ___ lives in Seoul, is an engineer.|that;who;which;whose|1|inson, vergulli → who
C|Tashkent, ___ is the capital, has a metro.|that;who;which;where|2|narsa, vergulli → which (that emas)
G|He passed the exam, ___ surprised everyone.|which / that|which|butun gapga ishora → which
G|My grandmother, ___ is 90, still reads every day.|who / which|who|inson → who
F|My car, that I bought last year, is red.|My car, which I bought last year, is red.|vergulli gapda that yo'q
F|Samarkand which is very old attracts tourists.|Samarkand, which is very old, attracts tourists.|vergul bilan ajratiladi
T|Bir gapga birlashtiring (who)|My mother is a teacher. She loves books.|My mother, who is a teacher, loves books.|, who …,
T|Bir gapga birlashtiring (which)|She didn't call. That made me worried.|She didn't call, which made me worried.|, which
""",
        "b2-linking" to """
C|___ it was cold, we went out.|Despite;Although;However;In spite|1|+ to'liq gap → although
C|___ the rain, we enjoyed the trip.|Although;Despite;However;Even though|1|+ ot → despite
G|It was expensive. ___, it was worth it.|however / despite|However|yangi gap boshida → However
G|In spite ___ being tired, she kept working.|of|of|in spite of + -ing
F|Despite it was late, we continued.|Although it was late, we continued.~Despite the late hour, we continued.~Even though it was late, we continued.|despite + gap emas
F|Although the traffic, we arrived on time.|Despite the traffic, we arrived on time.~Although there was traffic, we arrived on time.|although + to'liq gap
T|"despite" bilan yozing|Although he was ill, he went to work.|Despite being ill, he went to work.~Despite his illness, he went to work.|despite + -ing
T|"However" bilan ikki gapga ajrating|Although the hotel was small, it was comfortable.|The hotel was small. However, it was comfortable.|However, …
""",
        "b2-would-rather" to """
C|I'd rather ___ at home tonight.|to stay;stay;staying;stayed|1|would rather + 1-shakl
C|You'd better ___ now or you'll miss the train.|leave;to leave;leaving;left|0|had better + 1-shakl
G|I'd rather walk ___ take a taxi.|than|than|rather … than
G|You'd better ___ be late again.|not|not|had better not
F|I'd rather to go by train.|I'd rather go by train.|to yo'q
F|You had better to take a coat.|You had better take a coat.~You'd better take a coat.|had better + fe'l
T|"I'd rather" bilan yozing|I prefer tea to coffee.|I'd rather have tea than coffee.~I would rather have tea than coffee.~I'd rather drink tea than coffee.|would rather … than
T|"You'd better" bilan yozing|It's a good idea for you to see a doctor.|You'd better see a doctor.~You had better see a doctor.|had better + fe'l
""",
        "b2-participle" to """
C|___ home, I met an old friend.|Walked;Walking;Having walk;To walk|1|bir vaqtda, faol → -ing
C|___ the work, she went home.|Finishing;Having finished;Finished;To finish|1|avval tugadi → Having + V3
G|___ in 1900, the house needs repairs.|build|Built|majhul ma'no → V3
G|___ tired, he went to bed early.|feel|Feeling|faol → -ing
F|Having finish the test, he left.|Having finished the test, he left.|Having + V3
F|Writing in 1926, the novel is still popular.|Written in 1926, the novel is still popular.|majhul → Written
T|Qisqartiring (-ing bilan)|While I was waiting for the bus, I read a book.|Waiting for the bus, I read a book.|-ing clause
T|Qisqartiring (Having bilan)|After she had lost her keys, she called her husband.|Having lost her keys, she called her husband.|Having + V3
"""
    )
}
