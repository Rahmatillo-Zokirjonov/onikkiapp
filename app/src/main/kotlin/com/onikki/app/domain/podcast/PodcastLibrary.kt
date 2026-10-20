package com.onikki.app.domain.podcast

/** Built-in podcasts: 1, 2 and 3 minutes for each level, with six questions each (line = transcript line). */
object PodcastLibrary {
    val ALL: List<Podcast> by lazy { listOf(a1, a2, b1, b2).flatten() }

    private val a1 = listOf(
        Podcasts.parse(
            "pa1-1", "A1", 1, "My Day", "Kun tartibim",
            """
            Hi, my name is Laylo and I am twelve years old.
            I live with my family in a small flat in Namangan.
            Every day I get up at half past six.
            I have breakfast with my brother. We usually eat bread, cheese and eggs.
            School starts at eight o'clock, and I walk there with my friend Zarina.
            My favourite subject is English because our teacher is very funny.
            After school, I do my homework and help my mother in the kitchen.
            In the evening, we watch TV together.
            I go to bed at ten o'clock.
            On Sundays, I don't go to school, so I play volleyball in the park.
            """,
            """
            C|How old is Laylo?|ten;eleven;twelve;thirteen|2|0
            S|Which city does Laylo live in?|Namangan|1
            C|What time does Laylo get up?|6:00;6:30;7:00;7:30|1|2
            TF|Laylo goes to school by bus.|F|4
            S|What is her favourite subject?|English|5
            TF|On Sundays she plays volleyball in the park.|T|9
            """
        ),
        Podcasts.parse(
            "pa1-2", "A1", 2, "At the Café", "Kafeda",
            """
            A: Hello! Welcome to Sunny Café. What would you like?
            B: Hi! Can I have a cup of tea, please?
            A: Of course. Black tea or green tea?
            B: Green tea, please. And do you have any cakes?
            A: Yes, we have chocolate cake, apple cake and carrot cake.
            B: Hmm. I don't like chocolate very much. How much is the apple cake?
            A: It's fifteen thousand som. The carrot cake is twelve thousand.
            B: OK, I'll have the carrot cake, please.
            A: Good choice! Is it for here or to take away?
            B: For here. I'm waiting for my friend. She is coming at four o'clock.
            A: No problem. Please sit by the window. It's sunny and warm there.
            B: Thank you. Oh, and can I have some water too?
            A: Sure. Would you like cold water or warm water?
            B: Cold water, please. It's very hot outside today.
            A: Yes, it's thirty-five degrees! Here is your tea, your cake and your water.
            B: Thank you very much. How much is it all together?
            A: The tea is five thousand, the cake is twelve thousand and the water is free. So that's seventeen thousand.
            B: Here you are. Can I pay by card?
            A: Yes, of course. Enjoy your cake!
            """,
            """
            C|What does the customer drink?|black tea;green tea;coffee;juice|1|3
            TF|The customer loves chocolate.|F|5
            C|How much is the carrot cake?|5,000 som;12,000 som;15,000 som;17,000 som|1|6
            S|What time is her friend coming?|four~4~four o'clock~at four|9
            TF|The water costs money.|F|16
            C|How does the customer pay?|cash;by card;by phone;she doesn't pay|1|17
            """
        ),
        Podcasts.parse(
            "pa1-3", "A1", 3, "Grandfather's Village", "Bobomning qishlog'i",
            """
            Today I want to tell you about my grandfather's village.
            It is called Chortoq and it is near the mountains, about two hours from the city by car.
            There are about three hundred houses in the village.
            Most houses have a big garden with apple trees, apricot trees and grapes.
            My grandfather is seventy years old, but he is very active.
            He gets up very early, at five o'clock, and works in his garden every morning.
            He also has two cows, ten chickens and an old grey donkey called Boychechak.
            My grandmother makes fresh bread in a tandoor every Friday. The whole street can smell it!
            There is one small school, one shop and a mosque in the village.
            The shop sells bread, sugar, rice and sweets, but it closes at seven in the evening.
            There isn't a cinema or a big supermarket, so people go to the city for shopping.
            In summer, the village is beautiful. The weather is warm, but not too hot, because of the mountains.
            Children swim in the river and play football until it is dark.
            In winter, it is very cold and there is a lot of snow. Sometimes the road is closed for two or three days.
            Every summer, I stay with my grandparents for one month.
            I help my grandfather with the animals and I pick apricots with my cousins.
            We don't have fast internet there, so we read books, play games and talk a lot.
            In the evening, we sit outside, drink tea and look at the stars. There are so many stars in the village sky!
            I love the city, but I think the village is the best place for a holiday.
            """,
            """
            C|How far is the village from the city by car?|one hour;two hours;three hours;five hours|1|1
            S|How old is the grandfather?|seventy~70|4
            C|What is the donkey's name?|Chortoq;Boychechak;Zarina;Laylo|1|6
            TF|There is a cinema in the village.|F|10
            C|What sometimes happens to the road in winter?|it is closed;it is new;it is busy;it is repaired|0|13
            S|How long does the speaker stay in the village every summer?|one month~a month~1 month|14
            """
        )
    )

    private val a2 = listOf(
        Podcasts.parse(
            "pa2-1", "A2", 1, "The Lost Phone", "Yo'qolgan telefon",
            """
            Last Saturday was a terrible day for me.
            I went to the bazaar with my sister to buy some fruit and vegetables.
            It was very busy and there were a lot of people.
            When we got home, I wanted to call my friend, but my phone wasn't in my bag.
            I was really worried because all my photos were on it.
            My sister called my number, and a man answered.
            He said he found the phone near the melon stall.
            We took a taxi back to the bazaar, and the man was waiting for us.
            He was a kind old man who sold melons.
            I wanted to give him some money, but he said no.
            So I bought two big melons from him instead!
            """,
            """
            S|Where did they go last Saturday?|bazaar~the bazaar~to the bazaar~market|1
            TF|The bazaar was quiet.|F|2
            C|Why was the speaker worried?|her photos were on the phone;the phone was new;it was expensive;she needed to call her mother|0|4
            C|Who answered the phone?|her friend;a man;a police officer;her sister|1|5
            S|Where did the man find the phone? (near the … stall)|melon~melon stall~the melon stall|6
            C|What did she do instead of giving money?|bought melons;said nothing;bought tea;gave flowers|0|10
            """
        ),
        Podcasts.parse(
            "pa2-2", "A2", 2, "A Weekend in Bukhara", "Buxoroda dam olish",
            """
            A: Hey Bekzod, are you free next weekend?
            B: I think so. Why? Do you have a plan?
            A: Yes! I'm going to visit Bukhara with my cousin. Do you want to come with us?
            B: That sounds great! How are you going to get there?
            A: We're going to take the fast train. It leaves Tashkent at seven in the morning and arrives at about half past eleven.
            B: That's early, but it's OK. How much are the tickets?
            A: About two hundred thousand som each way. I bought mine yesterday.
            B: Hmm, that's not cheap, but I've saved some money this month. Where are you going to stay?
            A: My cousin found a small guest house near the old city. It's quite cheap and the reviews are very good.
            B: Perfect. What are you going to do there?
            A: On Saturday we're going to walk around the old city and see the Ark fortress. In the evening we want to try the famous Bukhara plov.
            B: And on Sunday?
            A: On Sunday morning we're going to visit a carpet market. My mum wants a small carpet for the living room.
            B: Nice. When are you coming back?
            A: On Sunday evening. The train leaves at six.
            B: OK, I'll check with my parents tonight and I'll call you tomorrow.
            A: Great. But don't wait too long. The tickets are selling fast!
            """,
            """
            S|Which city are they going to visit?|Bukhara|2
            C|What time does the train leave Tashkent?|6 am;7 am;11:30 am;6 pm|1|4
            TF|Bekzod has saved some money this month.|T|7
            C|Where are they going to stay?|in a big hotel;with family;in a guest house;in a tent|2|8
            S|What does the mum want from the market?|a carpet~carpet~a small carpet|12
            TF|Bekzod says yes at once without asking anyone.|F|15
            """
        ),
        Podcasts.parse(
            "pa2-3", "A2", 3, "How I Learned to Cook", "Ovqat pishirishni qanday o'rgandim",
            """
            When I was a student, I couldn't cook at all.
            I lived alone in a small room, and every day I ate sandwiches or bought fast food.
            After a few months, I felt tired all the time and I spent too much money.
            One day, I called my mother and asked her to teach me some easy dishes.
            She laughed, but she was very happy to help.
            The first dish she taught me was a simple vegetable soup.
            I needed potatoes, carrots, onions, a little meat, salt and water.
            It took about an hour, and it was not perfect, but I was proud of it.
            After that, I tried to cook something new every week.
            I watched cooking videos on the internet, and I wrote the recipes in a small notebook.
            Some dishes were a disaster. Once I burned the rice so badly that my neighbour knocked on the door because of the smoke!
            But slowly I got better.
            After about a year, I could make soup, salads, pasta and even plov.
            My friends started to visit me on Fridays because they liked my food.
            Cooking at home also helped me to save money. I spent about half of what I spent on fast food.
            Now I'm thirty years old and I still love cooking.
            My advice is simple: start with easy dishes, don't be afraid of mistakes, and cook for your friends.
            Food always tastes better when you share it.
            Last month, I even cooked dinner for my parents for the first time. My mother said my plov was almost as good as hers!
            For me, that was the best compliment in the world, and next year I want to take a real cooking course.
            """,
            """
            C|What did the speaker eat every day as a student?|soup;sandwiches or fast food;plov;salads|1|1
            S|Who taught the speaker to cook?|mother~his mother~her mother~my mother~mum|3
            C|What was the first dish?|plov;pasta;vegetable soup;salad|2|5
            TF|The neighbour came because of the smoke.|T|10
            C|When did friends start to visit?|on Mondays;on Fridays;every day;on Sundays|1|13
            S|How old is the speaker now?|thirty~30|15
            """
        )
    )

    private val b1 = listOf(
        Podcasts.parse(
            "pb1-1", "B1", 1, "The First Hour", "Kunning birinchi soati",
            """
            Many successful people say that the first hour of the day is the most important.
            Instead of checking their phone as soon as they wake up, they use this time for themselves.
            Some people exercise for twenty minutes, others read a few pages of a book or write a short plan for the day.
            Psychologists explain that these small habits give us a feeling of control.
            When we start the day calmly, we usually make better decisions later.
            However, a perfect routine doesn't have to be long.
            Even ten minutes without a screen can make a difference.
            The key is to be consistent and to choose activities that you actually enjoy.
            If you hate running, a morning run will not last more than a week.
            So try something small tomorrow, and see how you feel.
            """,
            """
            C|What do successful people avoid in the morning?|breakfast;checking their phone;exercise;reading|1|1
            S|How many minutes do some people exercise?|twenty~20|2
            C|What do small habits give us, according to psychologists?|more money;a feeling of control;more friends;better sleep|1|3
            TF|A good routine must be at least one hour long.|F|5
            TF|Ten minutes without a screen can help.|T|6
            C|Why might a morning run not last?|it is expensive;you hate running;it is dangerous;you have no time|1|8
            """
        ),
        Podcasts.parse(
            "pb1-2", "B1", 2, "Career Talk: Interviews", "Ish suhbatiga tayyorlanish",
            """
            A: Welcome back to Career Talk. Today I'm speaking with Dilnoza, who works in human resources. Dilnoza, what's the most common mistake people make in interviews?
            B: Honestly, it's not preparing. Many candidates haven't even read about the company before they come.
            A: Really? That seems obvious.
            B: It does, but you'd be surprised. If you know what the company does, you can explain why you want to work there, and that makes a big difference.
            A: What about nerves? A lot of our listeners say they get very nervous.
            B: That's completely normal. My advice is to practise answering common questions out loud, maybe with a friend. The more you practise, the calmer you feel.
            A: Which questions should people prepare for?
            B: Things like "Tell me about yourself", "What are your strengths?" and "Why did you leave your last job?"
            A: And how long should an answer be?
            B: About one or two minutes. If you talk for five minutes, the interviewer stops listening.
            A: Good point. What about clothes?
            B: Dress a little more formally than the people who work there. And arrive ten or fifteen minutes early, not an hour early!
            A: Finally, should candidates ask questions at the end?
            B: Definitely. If you don't ask anything, it looks like you aren't interested. Ask about the team or about training opportunities.
            A: Dilnoza, thank you so much. That was really useful.
            """,
            """
            C|What is Dilnoza's job?|teacher;human resources;doctor;journalist|1|0
            C|What is the most common mistake?|being late;not preparing;wearing jeans;talking too little|1|1
            S|Who can you practise with, according to Dilnoza?|a friend~friend~with a friend|5
            C|How long should an answer be?|30 seconds;1-2 minutes;5 minutes;10 minutes|1|9
            TF|You should arrive one hour early.|F|11
            TF|Asking questions at the end shows interest.|T|13
            """
        ),
        Podcasts.parse(
            "pb1-3", "B1", 3, "The Story of Plastic", "Plastik tarixi",
            """
            Plastic is one of the most useful materials ever invented, but it has also become one of our biggest problems.
            The first fully synthetic plastic was created in 1907, and by the 1950s, it was being used everywhere.
            It is cheap, light and strong, so it's perfect for packaging, toys, cars and medical equipment.
            The problem is that most plastic doesn't disappear. A plastic bottle can stay in nature for more than four hundred years.
            Every year, millions of tonnes of plastic end up in rivers and oceans.
            Sea animals often confuse plastic bags with food, and many of them die because of it.
            Scientists have also found tiny pieces of plastic, called microplastics, in fish, in salt and even in drinking water.
            So what can be done?
            Recycling helps, but only a small part of all plastic is actually recycled. In many countries, it's less than ten percent.
            That's why experts say the most important step is to use less plastic in the first place.
            Some countries have banned free plastic bags in shops, and people have quickly got used to bringing their own bags.
            Companies are also testing new materials made from plants, which break down much faster.
            As individuals, we can make small changes too.
            We can carry a reusable water bottle, say no to plastic straws and choose products with less packaging.
            These changes may seem small, but if millions of people make them, the effect is huge.
            In the end, plastic isn't the enemy. The way we use it is.
            """,
            """
            S|In which year was the first fully synthetic plastic created?|1907|1
            C|How long can a plastic bottle stay in nature?|40 years;100 years;more than 400 years;10 years|2|3
            C|Why do many sea animals die?|they eat plastic thinking it is food;the water is too cold;fishing boats;noise|0|5
            S|What are tiny pieces of plastic called?|microplastics~microplastic|6
            TF|In many countries, more than half of plastic is recycled.|F|8
            C|What is the most important step, according to experts?|recycle more;use less plastic;clean the oceans;make plastic cheaper|1|9
            """
        )
    )

    private val b2 = listOf(
        Podcasts.parse(
            "pb2-1", "B2", 1, "The Myth of Multitasking", "Bir vaqtda ko'p ish afsonasi",
            """
            Most of us believe we're good at multitasking, but research suggests otherwise.
            When we switch between tasks, such as answering messages while writing a report, our brain doesn't actually do both at once.
            Instead, it jumps back and forth, and each switch costs time and energy.
            Some studies estimate that frequent switching can reduce productivity by up to forty percent.
            What's more, people who multitask heavily tend to make more mistakes and remember less of what they've done.
            Interestingly, those who consider themselves the best multitaskers often perform the worst in tests.
            The alternative is called single-tasking: focusing on one thing for a fixed period, then taking a short break.
            It may feel slower at first, but in the long run, it usually leads to better results and less stress.
            """,
            """
            C|What does the brain actually do when we multitask?|does both at once;jumps between tasks;stops working;slows down completely|1|2
            S|By up to what percentage can switching reduce productivity?|forty~40~40 percent~forty percent|3
            TF|Heavy multitaskers remember more of what they've done.|F|4
            C|Who often performs worst in tests?|people who never multitask;people who think they are the best multitaskers;students;managers|1|5
            S|What is the alternative called?|single-tasking~single tasking~singletasking|6
            TF|Single-tasking may feel slower at first.|T|7
            """
        ),
        Podcasts.parse(
            "pb2-2", "B2", 2, "Should We Work From Home?", "Uydan ishlash: bahs",
            """
            A: Today's question is a big one: should companies let people work from home permanently? Timur, you've been working remotely for three years. What's your view?
            B: For me, it's been life-changing. I used to spend nearly two hours a day commuting. Now I use that time to exercise and to spend time with my kids.
            A: But don't you feel isolated?
            B: At the beginning I did, I'll admit. I missed chatting with colleagues. So now I go to a co-working space twice a week, which helps a lot.
            A: Some managers argue that productivity falls when people aren't in the office. Would you agree?
            B: Not in my experience. If anything, I get more done because there are fewer interruptions. But it depends on the person. You need a lot of self-discipline.
            A: What about young employees who've just started their careers?
            B: That's a fair point. They probably lose out the most. Learning by watching experienced colleagues is much harder over video calls.
            A: So is a hybrid model the answer?
            B: I think so. Two or three days in the office for collaboration and training, and the rest at home for focused work.
            A: And if your company suddenly said everyone must return full-time?
            B: Honestly, I'd start looking for another job. Flexibility has become one of the main things people look for, not just salary.
            A: Interesting. Thanks for sharing your experience, Timur.
            """,
            """
            S|How long has Timur been working remotely?|three years~3 years|0
            C|How much time did Timur spend commuting each day?|30 minutes;one hour;nearly two hours;three hours|2|1
            C|What does Timur do to feel less isolated?|calls his family;goes to a co-working space;works in a café;joins a gym|1|3
            TF|Timur believes remote work suits everyone equally.|F|5
            C|Who loses out the most, according to Timur?|managers;young employees;parents;IT workers|1|7
            TF|Timur would probably change jobs if forced to return full-time.|T|11
            """
        ),
        Podcasts.parse(
            "pb2-3", "B2", 3, "Why We Procrastinate", "Nega ishni ortga suramiz",
            """
            Almost everyone procrastinates from time to time. We delay writing the essay, answering the difficult email or starting the diet, even when we know that delaying will make things worse.
            For a long time, procrastination was seen simply as laziness or poor time management.
            However, psychologists now argue that it's mainly an emotional problem rather than a time problem.
            When a task makes us feel anxious, bored or insecure, our brain looks for a quick way to feel better, and the easiest option is to avoid the task altogether.
            Scrolling through social media or tidying the desk gives us instant relief. Unfortunately, that relief doesn't last, and the task is still waiting for us, now with added guilt.
            This creates a cycle: the more we delay, the worse we feel about the task, and the more likely we are to delay it again.
            So how can this cycle be broken?
            One widely recommended strategy is to make the first step ridiculously small. Instead of "write the report", the goal becomes "open the document and write one sentence".
            Once we've started, continuing is usually much easier than we expected.
            Another technique is to be kind to ourselves. Research has shown that students who forgave themselves for procrastinating on one exam procrastinated less before the next one.
            Criticising ourselves, on the other hand, tends to increase the negative emotions that caused the problem in the first place.
            It also helps to remove temptations. Putting the phone in another room, for example, makes distraction a little less convenient.
            Finally, some people find it useful to work in short, timed sessions, such as twenty-five minutes of work followed by a five-minute break.
            None of these methods is magic, but together they can change our relationship with difficult tasks.
            The important thing to remember is that procrastination isn't a character flaw. It's a habit, and habits can be changed.
            """,
            """
            C|How do psychologists now see procrastination?|as laziness;mainly as an emotional problem;as a time problem;as an illness|1|2
            C|What does avoiding a task give us?|long-term happiness;instant relief;more energy;better grades|1|4
            S|According to the strategy, how should the first step be?|small~very small~ridiculously small~tiny|7
            TF|Students who forgave themselves procrastinated more before the next exam.|F|9
            C|Where can you put your phone to reduce distraction?|in your pocket;in another room;on the desk;in your bag|1|11
            S|How many minutes of work are in one timed session?|twenty-five~25~twenty five|12
            """
        )
    )
}
