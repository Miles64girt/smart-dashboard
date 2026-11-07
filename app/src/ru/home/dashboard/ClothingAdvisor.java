package ru.home.dashboard;

/** Короткие практичные рекомендации по одежде на основе сегодняшней погоды. */
public final class ClothingAdvisor {

    /** Возвращает 5 строк: одежда, верх, зонт, обувь, аксессуары. */
    public static String[] advise(WeatherData w) {
        double f = w.feels;          // ощущаемая температура — основа решений
        double wind = w.windMs;
        double precip = w.precipProb;

        String clothes;
        if (f >= 26)      clothes = "Футболка, шорты или лёгкие брюки";
        else if (f >= 22) clothes = "Футболка, лёгкие брюки";
        else if (f >= 18) clothes = "Лонгслив, джинсы";
        else if (f >= 14) clothes = "Свитер или худи, джинсы";
        else if (f >= 10) clothes = "Тёплый свитер, плотные брюки";
        else if (f >= 6)  clothes = "Свитер, утеплённые брюки";
        else if (f >= 2)  clothes = "Термобельё, свитер, тёплые брюки";
        else if (f >= -3) clothes = "Термобельё, тёплый свитер";
        else if (f >= -10) clothes = "Термобельё, флис, тёплый свитер";
        else              clothes = "Термобельё, флис, пуховый свитер";

        String outer;
        if (f >= 26)      outer = "Не нужна";
        else if (f >= 20) outer = "Лёгкая ветровка";
        else if (f >= 15) outer = "Ветровка или джинсовка";
        else if (f >= 10) outer = "Куртка";
        else if (f >= 4)  outer = "Тёплая куртка";
        else if (f >= -3) outer = "Зимняя куртка или пуховик";
        else              outer = "Пуховик, капюшон";

        String umbrella;
        if (w.isSnow() && precip >= 40) umbrella = "Да, или капюшон";
        else if (precip >= 50) umbrella = "Да, возьмите зонт";
        else if (precip >= 25) umbrella = "Возможен, лучше взять";
        else umbrella = "Не нужен";

        String shoes;
        if (f <= 0 || (w.isSnow() && precip >= 30)) shoes = "Тёплые ботинки, нескользящая подошва";
        else if (precip >= 40) shoes = "Водонепроницаемая обувь";
        else if (f >= 22) shoes = "Кроссовки или сандалии";
        else shoes = "Закрытые кроссовки или ботинки";

        String extras;
        if (f <= 0 && wind >= 8) extras = "Шапка, перчатки, шарф";
        else if (f <= 0) extras = "Шапка и перчатки";
        else if (wind >= 8) extras = "Шарф, ветрозащита";
        else if (w.precipProb >= 50) extras = "Влажные салфетки для обуви";
        else extras = "Без лишнего";

        return new String[] { clothes, outer, umbrella, shoes, extras };
    }
}
