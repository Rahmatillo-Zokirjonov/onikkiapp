package com.onikki.app.domain.grammar

/**
 * Built-in exercises for A1–A2, one line each (see GrammarBank.parse):
 * C|gap sentence|opt;opt;opt;opt|right index|why    — test
 * G|gap sentence|hint|answer~answer|why             — gap fill
 * F|wrong sentence|right~right|why                  — fix the mistake
 * T|instruction|sentence|answer~answer|why          — transform
 */
internal object GrammarDrillsA {
    val ALL: Map<String, String> = mapOf(
        "a1-be" to """
C|My sister ___ a teacher.|is;are;am;be|0|she → is
C|We ___ from Fergana.|is;am;are;be|2|we → are
G|I ___ very tired today.|be|am~'m|I bilan am ishlatiladi
G|___ they at school now?|be|Are|so'roqda fe'l oldinga chiqadi: they → are
F|He are my best friend.|He is my best friend.|he → is
F|I is hungry.|I am hungry.~I'm hungry.|I → am
T|Inkor qiling|She is at home.|She is not at home.~She isn't at home.~She's not at home.|is + not
T|So'roq qiling|You are a student.|Are you a student?|fe'l egadan oldinga chiqadi
""",
        "a1-present-simple" to """
C|She ___ coffee every morning.|drink;drinks;drinking;is drink|1|he/she/it → fe'l + s
C|They ___ in a big city.|lives;living;live;is live|2|they bilan -s yo'q
G|My father ___ in a hospital.|work|works|he → works
G|___ you like pizza?|do|Do|you bilan so'roq: Do
F|He go to school by bus.|He goes to school by bus.|he → goes (-es)
F|She doesn't likes tea.|She doesn't like tea.|doesn't dan keyin fe'l -s siz
T|Inkor qiling|I play tennis.|I don't play tennis.~I do not play tennis.|don't + fe'l
T|So'roq qiling|He speaks French.|Does he speak French?|Does + ega + fe'l (-s siz)
""",
        "a1-present-continuous" to """
C|Look! The baby ___.|sleeps;is sleeping;sleeping;sleep|1|hozir bo'layotgan ish: is + -ing
C|We ___ dinner at the moment.|are having;have;having;has|0|at the moment → davomli zamon
G|I ___ a letter now.|write|am writing~'m writing|am + writing (e tushadi)
G|They ___ in the park right now.|run|are running~'re running|run → running (n ikkilanadi)
F|She is cook dinner now.|She is cooking dinner now.|is + fe'l-ing
F|I watching TV at the moment.|I am watching TV at the moment.~I'm watching TV at the moment.|am tushib qolgan
T|So'roq qiling|You are listening to me.|Are you listening to me?|Are oldinga chiqadi
T|Inkor qiling|He is working today.|He is not working today.~He isn't working today.~He's not working today.|is + not
""",
        "a1-articles" to """
C|I have ___ umbrella.|a;an;the;-|1|unli tovush oldidan an
C|___ sun is very hot today.|A;An;The;-|2|yagona, hammaga ma'lum narsa: the
G|She bought ___ new car.|a / an|a|undosh tovush: a new
G|Please open ___ window.|a / the|the|ikkalamiz biladigan deraza: the
F|He is a honest man.|He is an honest man.|honest — h o'qilmaydi, unli tovush: an
F|I like the music very much.|I like music very much.|umuman musiqa — artiklsiz
T|"a" bilan to'ldirib qayta yozing: bitta|I need pen.|I need a pen.|sanaladigan birlik ot oldidan a
T|Ko'plikka aylantiring|I have a cat.|I have cats.|ko'plikda a yo'qoladi
""",
        "a1-plurals" to """
C|There are three ___ in the class.|child;childs;children;childrens|2|child → children
C|I need two ___.|box;boxs;boxes;boxies|2|-x → -es
G|These ___ are delicious.|potato|potatoes|-o → -es
G|Many ___ visit Samarkand.|person|people|person → people
F|I have two brother.|I have two brothers.|raqamdan keyin ko'plik
F|My foots are cold.|My feet are cold.|foot → feet
T|Ko'plikka aylantiring|The city is big.|The cities are big.|city → cities, is → are
T|Ko'plikka aylantiring|This knife is sharp.|These knives are sharp.|knife → knives, this → these
""",
        "a1-there-is" to """
C|___ a cat on the sofa.|There is;There are;It is;Is there|0|bitta → There is
C|___ any eggs in the fridge?|Is there;There are;Are there;Have|2|ko'plik so'roq → Are there
G|There ___ two banks in this street.|be|are|two banks → are
G|There ___ a problem with my phone.|be|is|a problem → is
F|There are a park near my house.|There is a park near my house.|a park — birlik
F|There is many people here.|There are many people here.|people — ko'plik
T|So'roq qiling|There is a café here.|Is there a café here?|Is there…?
T|Inkor qiling (any bilan)|There are some chairs.|There aren't any chairs.~There are not any chairs.|inkorda some → any
""",
        "a1-possessives" to """
C|This is Ali and this is ___ sister.|his;her;he;him|0|Ali (erkak) → his
C|We love ___ teacher.|us;we;our;ours|2|we → our
G|They are washing ___ car.|they|their|they → their
G|Is this ___ pen? (you)|you|your|you → your
F|She name is Nilufar.|Her name is Nilufar.|she → her
F|This is Bobur book.|This is Bobur's book.|insonga tegishli: 's
T|"'s" bilan qayta yozing|The car of my father is new.|My father's car is new.|egalik: father's
T|Egalik so'zi bilan almashtiring|This is the bag of Madina.|This is her bag.|Madina → her
""",
        "a1-can" to """
C|She ___ swim very well.|cans;can;can to;is can|1|can o'zgarmaydi
C|___ you help me?|Do;Are;Can;Does|2|iltimos: Can you…?
G|I ___ drive. I'm only fifteen.|can / can't|can't~cannot~can not|ma'no: haydash mumkin emas
G|My grandmother ___ speak Russian.|can|can|can + fe'l
F|He can to play the guitar.|He can play the guitar.|can dan keyin to yo'q
F|She cans cook very well.|She can cook very well.|can ga -s qo'shilmaydi
T|So'roq qiling|You can ride a bike.|Can you ride a bike?|Can oldinga chiqadi
T|Inkor qiling|I can come tomorrow.|I can't come tomorrow.~I cannot come tomorrow.~I can not come tomorrow.|can't + fe'l
""",
        "a1-prep-place" to """
C|The book is ___ the table.|in;on;at;under|1|ustida → on
C|My shoes are ___ the bed.|under;on;between;at|0|tagida → under
G|Put the milk ___ the fridge.|in / on|in|ichida → in
G|The shop is ___ the bank and the café.|between / next|between|ikki narsa o'rtasida → between
F|The picture is in the wall.|The picture is on the wall.|devorda (ustida) → on
F|The cat is under of the table.|The cat is under the table.|under dan keyin of yo'q
T|Qarama-qarshi joyni yozing (on → under)|The ball is on the box.|The ball is under the box.|on ↔ under
T|"in front of" ni "behind" ga almashtiring|The car is in front of the house.|The car is behind the house.|behind — orqasida
""",
        "a1-prep-time" to """
C|My birthday is ___ March.|on;at;in;by|2|oy → in
C|The film starts ___ 8 o'clock.|in;on;at;to|2|soat → at
G|We have English ___ Monday.|at / on / in|on|kun → on
G|I usually read ___ the evening.|at / on / in|in|kun qismi → in the evening
F|I was born on 2009.|I was born in 2009.|yil → in
F|See you in Friday!|See you on Friday!|kun → on
T|"next week" bilan yozing (predlogsiz)|I will call you on Monday.|I will call you next week.|next oldidan predlog yo'q
T|Vaqtni "at night" ga almashtiring|I study in the afternoon.|I study at night.|at night
""",
        "a1-have-got" to """
C|She ___ two cats.|have;has;haves;is have|1|she → has
C|___ you have a pen?|Does;Are;Do;Have|2|you → Do
G|My brother ___ a new bike.|have|has|he → has
G|We don't ___ any money.|have|have|don't + have
F|He have a big house.|He has a big house.|he → has
F|Does she has a car?|Does she have a car?|does dan keyin have
T|Inkor qiling|They have a garden.|They don't have a garden.~They do not have a garden.~They haven't got a garden.|don't have
T|So'roq qiling|She has a sister.|Does she have a sister?~Has she got a sister?|Does + she + have
""",
        "a1-questions" to """
C|___ do you live? — In Tashkent.|What;Where;When;Who|1|joy → Where
C|___ is your teacher? — Mr Karimov.|Who;Which;What;Whose|0|inson → Who
G|___ old are you?|how / what|How|yosh: How old
G|___ apples do you want? — Three.|how many / how much|How many|sanaladigan → How many
F|Where you live?|Where do you live?|do tushib qolgan
F|What time the lesson starts?|What time does the lesson start?|does + start (-s siz)
T|Javobga savol tuzing (Where)|I work in a bank.|Where do you work?|Where + do + you + work
T|Javobga savol tuzing (When)|She gets up at six.|When does she get up?|When + does + she + get up
""",
        // ------------------------------------------------------------ A2
        "a2-past-simple" to """
C|We ___ to the cinema last night.|go;went;goed;gone|1|go → went (V2)
C|She ___ English in London in 2019.|study;studyed;studied;studies|2|study → studied
G|I ___ my grandmother yesterday.|visit|visited|-ed qo'shiladi
G|They ___ a new house last year.|buy|bought|buy → bought
F|He stoped the car.|He stopped the car.|stop → stopped
F|I see her yesterday.|I saw her yesterday.|yesterday → o'tgan zamon: saw
T|O'tgan zamonga aylantiring (yesterday)|I play football.|I played football yesterday.|play → played
T|O'tgan zamonga aylantiring (last week)|She writes a letter.|She wrote a letter last week.|write → wrote
""",
        "a2-past-questions" to """
C|___ you see the match yesterday?|Do;Did;Were;Was|1|o'tgan zamon so'rog'i: Did
C|I didn't ___ him at the party.|saw;see;seen;seeing|1|didn't + 1-shakl
G|Where ___ you go last summer?|do|did|o'tgan zamon: did
G|She ___ at home yesterday. (be, inkor)|be|wasn't~was not|be bilan did emas: wasn't
F|I didn't went to school.|I didn't go to school.|didn't + go
F|Did she called you?|Did she call you?|did + call
T|Inkor qiling|He finished his work.|He didn't finish his work.~He did not finish his work.|didn't + finish
T|So'roq qiling|They lived in Bukhara.|Did they live in Bukhara?|Did + they + live
""",
        "a2-past-continuous" to """
C|I ___ TV when you called.|watched;was watching;were watching;watch|1|davomli ish: was + -ing
C|They ___ football at 5 pm.|was playing;were playing;played;are playing|1|they → were
G|While I ___ home, it started to rain.|walk|was walking|while + davomli
G|What ___ you doing at 9 o'clock?|be|were|you → were
F|She were reading a book.|She was reading a book.|she → was
F|I was cook when he arrived.|I was cooking when he arrived.|was + cooking
T|Past Continuous ga aylantiring (at 8 pm)|I read a book.|I was reading a book at 8 pm.|was + reading
T|So'roq qiling|They were sleeping.|Were they sleeping?|Were oldinga chiqadi
""",
        "a2-will-going-to" to """
C|Look at those clouds! It ___ rain.|will;is going to;going;is going|1|ko'rinib turgan natija: going to
C|The phone is ringing. — I ___ answer it.|am going to;will;going to;am|1|shu zahoti qaror: will
G|We ___ visit Paris next month. (reja)|be going to|are going to~'re going to|oldindan reja: going to
G|I think she ___ pass the exam.|will|will|taxmin: will
F|I will to call you tomorrow.|I will call you tomorrow.|will dan keyin to yo'q
F|She going to buy a car.|She is going to buy a car.~She's going to buy a car.|is tushib qolgan
T|Inkor qiling|I will tell him.|I won't tell him.~I will not tell him.|won't
T|"going to" bilan yozing|They plan to move house.|They are going to move house.~They're going to move house.|reja → going to
""",
        "a2-comparatives" to """
C|My car is ___ than yours.|fast;faster;fastest;more fast|1|qisqa sifat + er
C|This is ___ book I have ever read.|the better;the best;the good;best|1|good → the best
G|Gold is ___ than silver.|expensive|more expensive|uzun sifat → more
G|Today is the ___ day of the year.|hot|hottest|hot → hottest (t ikkilanadi)
F|She is more tall than me.|She is taller than me.|qisqa sifat: taller
F|This is the most cheap shop.|This is the cheapest shop.|cheap → the cheapest
T|Solishtiring (bigger)|My room is big. Your room is small.|My room is bigger than your room.~My room is bigger than yours.|big → bigger than
T|"as … as" bilan yozing|Ali is tall. Vali is tall too.|Ali is as tall as Vali.|teng: as … as
""",
        "a2-countable" to """
C|How ___ water do you drink a day?|many;much;lot;few|1|water — sanalmaydi: much
C|There aren't ___ apples left.|some;any;much;a|1|inkor → any
G|How ___ brothers do you have?|much / many|many|sanaladigan → many
G|I'd like ___ tea, please.|some / any|some|taklif / darak: some
F|Can you give me an advice?|Can you give me some advice?~Can you give me advice?|advice sanalmaydi
F|I don't have some money.|I don't have any money.|inkor → any
T|So'roq qiling (any bilan)|There is some milk.|Is there any milk?|so'roqda some → any
T|"a lot of" bilan almashtiring|She has many friends.|She has a lot of friends.|a lot of — ikkalasiga
""",
        "a2-present-perfect" to """
C|I ___ never been to London.|has;have;am;did|1|I → have
C|She ___ just finished her homework.|have;has;is;had|1|she → has
G|We ___ this film already.|see|have seen~'ve seen|have + V3 (seen)
G|___ you ever eaten sushi?|have|Have|so'roq: Have you ever…
F|I have saw this film.|I have seen this film.|have + V3: seen
F|He have lost his keys.|He has lost his keys.|he → has
T|Present Perfect ga aylantiring (already)|I finished the book.|I have already finished the book.~I've already finished the book.|have + already + V3
T|Inkor qiling (yet)|She has arrived.|She hasn't arrived yet.~She has not arrived yet.|hasn't … yet
""",
        "a2-frequency" to """
C|She ___ late for work.|never is;is never;never;is not never|1|be dan keyin: is never
C|I ___ go to the gym on Mondays.|usually;am usually;usual;use|0|asosiy fe'ldan oldin
G|They ___ eat meat. (0%)|never / always|never|0% → never
G|He is ___ happy. (100%)|always / never|always|100% → always
F|I go always to bed at ten.|I always go to bed at ten.|ravish asosiy fe'ldan oldin
F|She sometimes is busy.|She is sometimes busy.|be dan keyin
T|"often" ni joyiga qo'ying|We play chess.|We often play chess.|fe'ldan oldin
T|"usually" ni joyiga qo'ying|He is tired after work.|He is usually tired after work.|is dan keyin
""",
        "a2-must-have-to" to """
C|You ___ smoke here. It's forbidden.|mustn't;don't have to;must;have to|0|taqiq → mustn't
C|It's Sunday. I ___ get up early.|mustn't;don't have to;must;has to|1|shart emas → don't have to
G|She ___ wear a uniform at school.|have to|has to|she → has to
G|Yesterday I ___ wait for an hour.|have to|had to|o'tmish → had to
F|He musts study harder.|He must study harder.|must o'zgarmaydi
F|I must to go now.|I must go now.|must dan keyin to yo'q
T|O'tgan zamonga aylantiring|I have to work late.|I had to work late.|have to → had to
T|Inkor qiling (shart emas ma'nosida)|You have to pay.|You don't have to pay.~You do not have to pay.|don't have to
""",
        "a2-should" to """
C|You look ill. You ___ see a doctor.|should;shouldn't;must not;don't|0|maslahat → should
C|You ___ eat so much sugar.|should;shouldn't;can;will|1|salbiy maslahat → shouldn't
G|What ___ I do?|should|should|maslahat so'rash
G|He ___ drive so fast. It's dangerous.|should (inkor)|shouldn't~should not|shouldn't
F|You should to rest.|You should rest.|should dan keyin to yo'q
F|She shoulds study more.|She should study more.|should o'zgarmaydi
T|Maslahat qiling (should)|Drink more water.|You should drink more water.|You should + fe'l
T|So'roq qiling|I should call her.|Should I call her?|Should oldinga chiqadi
""",
        "a2-ing-to" to """
C|I enjoy ___ to music.|listen;to listen;listening;listened|2|enjoy + -ing
C|She wants ___ a doctor.|become;to become;becoming;became|1|want + to
G|We decided ___ at home.|stay|to stay|decide + to
G|Thank you for ___ me.|help|helping|predlogdan keyin -ing
F|I like swim in the sea.|I like swimming in the sea.~I like to swim in the sea.|like + -ing
F|He hopes winning the game.|He hopes to win the game.|hope + to
T|"enjoy" bilan yozing|I like to read books.|I enjoy reading books.|enjoy + -ing
T|"want" bilan yozing|I would like to travel.|I want to travel.|want + to
""",
        "a2-too-enough" to """
C|This tea is ___ hot to drink.|too;enough;very;so|0|ortiqcha → too
C|He isn't old ___ to drive.|too;enough;very;much|1|sifat + enough
G|We don't have ___ time.|enough / too|enough|enough + ot
G|The box is ___ heavy to carry.|too / enough|too|ortiqcha og'ir → too
F|She is enough old to vote.|She is old enough to vote.|enough sifatdan keyin
F|It's too much cold today.|It's too cold today.|too + sifat
T|"too" bilan yozing|The bag is very small. I can't use it.|The bag is too small.|too small
T|"enough" bilan yozing|He is very tall. He can reach the shelf.|He is tall enough to reach the shelf.|tall enough to
"""
    )
}
