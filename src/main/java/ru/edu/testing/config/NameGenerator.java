package ru.edu.testing.config;

import java.util.*;

/**
 * Вымышленные ФИО студентов (таджикские и русские имена) и логины транслитом.
 * Генерация детерминирована: при каждом заполнении базы получаются одни и те же люди.
 */
public class NameGenerator {

    private static final String[] SURNAMES = {
            "Абдуллоев", "Рахимов", "Саидов", "Каримов", "Назаров", "Шарипов", "Юсупов", "Холов", "Мирзоев",
            "Курбонов", "Давлатов", "Олимов", "Раджабов", "Ахмедов", "Бобоев", "Хакимов", "Нуриддинов", "Шокиров",
            "Ибрагимов", "Исмоилов", "Зоиров", "Мансуров", "Латипов", "Файзуллоев", "Джураев", "Сангинов",
            "Норов", "Азимов", "Турсунов", "Камолов", "Иванов", "Петров", "Смирнов", "Кузнецов", "Волков", "Соколов"};
    private static final String[] MALE = {
            "Фирдавс", "Шохрух", "Рустам", "Сухроб", "Далер", "Бехруз", "Мухаммад", "Абдулло", "Исмоил", "Парвиз",
            "Фаррух", "Джамшед", "Умед", "Носир", "Хуршед", "Шахзод", "Комрон", "Амир", "Алишер", "Азиз", "Илья",
            "Артём", "Даниил", "Максим", "Никита"};
    private static final String[] FEMALE = {
            "Фарзона", "Мадина", "Нигина", "Зарина", "Шахноза", "Малика", "Сабина", "Дилноза", "Мехрона", "Тахмина",
            "Ситора", "Фируза", "Гулнора", "Парвина", "Алина", "Анастасия", "Дарья", "Екатерина"};
    /** Отчества: {мужское, женское}. */
    private static final String[][] PATRONYMICS = {
            {"Рустамович", "Рустамовна"}, {"Абдуллоевич", "Абдуллоевна"}, {"Сафарович", "Сафаровна"},
            {"Шарифович", "Шарифовна"}, {"Каримович", "Каримовна"}, {"Джамшедович", "Джамшедовна"},
            {"Бахтиёрович", "Бахтиёровна"}, {"Фаррухович", "Фарруховна"}, {"Хуршедович", "Хуршедовна"},
            {"Умедович", "Умедовна"}, {"Нуруллоевич", "Нуруллоевна"}, {"Сайфуллоевич", "Сайфуллоевна"},
            {"Толибджонович", "Толибджоновна"}, {"Алексеевич", "Алексеевна"}, {"Сергеевич", "Сергеевна"},
            {"Андреевич", "Андреевна"}};

    private final Random random;
    private final Set<String> used = new HashSet<>();

    public NameGenerator(long seed) {
        this.random = new Random(seed);
    }

    public String next() {
        for (int attempt = 0; attempt < 1000; attempt++) {
            boolean female = random.nextInt(100) < 45;
            String surname = SURNAMES[random.nextInt(SURNAMES.length)] + (female ? "а" : "");
            String name = female ? FEMALE[random.nextInt(FEMALE.length)] : MALE[random.nextInt(MALE.length)];
            String patronymic = PATRONYMICS[random.nextInt(PATRONYMICS.length)][female ? 1 : 0];
            String full = surname + " " + name + " " + patronymic;
            if (used.add(full)) {
                return full;
            }
        }
        throw new IllegalStateException("Не удалось подобрать уникальное ФИО");
    }

    private static final Map<Character, String> TRANSLIT = new HashMap<>();

    static {
        String[] pairs = {"а:a", "б:b", "в:v", "г:g", "д:d", "е:e", "ё:yo", "ж:zh", "з:z", "и:i", "й:y", "к:k", "л:l",
                "м:m", "н:n", "о:o", "п:p", "р:r", "с:s", "т:t", "у:u", "ф:f", "х:kh", "ц:ts", "ч:ch", "ш:sh", "щ:sch",
                "ъ:", "ы:y", "ь:", "э:e", "ю:yu", "я:ya", "ӣ:i", "ӯ:u", "ҳ:h", "қ:q", "ғ:gh", "ҷ:j"};
        for (String p : pairs) {
            String[] kv = p.split(":", -1);
            TRANSLIT.put(kv[0].charAt(0), kv[1]);
        }
    }

    static String translit(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (TRANSLIT.containsKey(c)) {
                sb.append(TRANSLIT.get(c));
            } else if (Character.isLetterOrDigit(c) && c < 128) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Логин: фамилия транслитом + первые буквы имени и отчества.
     * «Абдуллоев Фирдавс Рустамович» → abdulloev.fr, «Алиев А.Б.» → aliev.ab.
     */
    public static String login(String fullName) {
        String[] parts = fullName.trim().split("[\\s.]+");
        StringBuilder initials = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                initials.append(translit(parts[i].substring(0, 1)));
            }
        }
        String login = translit(parts[0]) + (initials.length() > 0 ? "." + initials : "");
        return login.length() > 45 ? login.substring(0, 45) : login;
    }
}
