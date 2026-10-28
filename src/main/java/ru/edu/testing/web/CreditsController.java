package ru.edu.testing.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/** Страница с авторами и лицензиями фотографий (требование лицензий CC BY / CC BY-SA). */
@Controller
public class CreditsController {

    public record Credit(String image, String usage, String title, String author, String license, String url) {
    }

    static final List<Credit> CREDITS = List.of(
            new Credit("/img/hero-rtsu.jpg", "Страница входа", "Здание Российско-Таджикского (Славянского) университета",
                    "Foteh Rahimov", "Общественное достояние",
                    "https://commons.wikimedia.org/wiki/File:Donishgohi_slavyanii_Tojikistonu_Rusiya.JPG"),
            new Credit("/img/campus-rtsu.jpg", "Главная страница", "Новый корпус РТСУ",
                    "Шухрат Саъдиев", "CC BY-SA 4.0",
                    "https://commons.wikimedia.org/wiki/File:Новый_корпус_РТСУ.jpg"),
            new Credit("/img/library.jpg", "Регистрация", "Students studying in the library",
                    "Gary Todd", "CC0",
                    "https://commons.wikimedia.org/wiki/File:Sias_Library_-_Students_Studying_2017.jpg"),
            new Credit("/img/cover-db.jpg", "Обложка «Базы данных»", "BalticServers data center",
                    "BalticServers.com", "CC BY-SA 3.0",
                    "https://commons.wikimedia.org/wiki/File:BalticServers_data_center.jpg"),
            new Credit("/img/cover-code.jpg", "Обложка «Программирование»", "Code on computer monitor",
                    "Markus Spiske", "CC0",
                    "https://commons.wikimedia.org/wiki/File:Code_on_computer_monitor_(Unsplash).jpg"),
            new Credit("/img/cover-network.jpg", "Обложка «Компьютерные сети»", "Ethernet switch",
                    "Raysonho @ Open Grid Scheduler / Grid Engine", "CC0",
                    "https://commons.wikimedia.org/wiki/File:EthernetSwitch.jpg"),
            new Credit("/img/cover-security.jpg", "Обложка «Информационная безопасность»", "Padlock with chain",
                    "Lacz02", "CC BY-SA 4.0",
                    "https://commons.wikimedia.org/wiki/File:Padlock_with_chain.jpg"),
            new Credit("/img/cover-default.jpg", "Обложка по умолчанию", "Typing on a laptop",
                    "Simon Hattinga Verschure", "CC0",
                    "https://commons.wikimedia.org/wiki/File:Typing_on_a_laptop_(Unsplash).jpg"));

    @GetMapping("/credits")
    public String credits(Model model) {
        model.addAttribute("credits", CREDITS);
        return "credits";
    }
}
