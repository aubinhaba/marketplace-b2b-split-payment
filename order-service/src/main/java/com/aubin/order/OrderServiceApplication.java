package com.aubin.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Point d'entrée du microservice Order.
 *
 * <p><b>Position en hexagonal</b> : racine du package — ni domain, ni infrastructure.
 * C'est le "glue code" qui démarre le conteneur Spring et laisse l'auto-configuration
 * assembler les adapters autour du domain.
 *
 * <p>{@code @SpringBootApplication} est un alias pour trois annotations :
 * <ul>
 *   <li>{@code @Configuration} : cette classe peut déclarer des beans Spring</li>
 *   <li>{@code @EnableAutoConfiguration} : Spring Boot configure automatiquement
 *       les beans selon les dépendances présentes sur le classpath (JPA si spring-data-jpa,
 *       Tomcat si spring-web, Flyway si flyway-core...)</li>
 *   <li>{@code @ComponentScan} : Spring scanne récursivement le package
 *       {@code com.aubin.order} pour trouver les {@code @Component}, {@code @Service},
 *       {@code @Repository}, {@code @Controller}...</li>
 * </ul>
 *
 * <p>{@code @EnableScheduling} : active le scheduler Spring (thread pool de tâches planifiées).
 * Nécessaire pour l'Outbox poller (step F) qui utilise {@code @Scheduled(fixedDelay = ...)}.
 * On l'active ici dès le départ pour éviter un "pourquoi mon @Scheduled ne s'exécute pas ?"
 * silencieux plus tard.
 *
 * <p><b>Piège classique</b> : oublier {@code @EnableScheduling} et constater que le poller
 * ne se déclenche jamais — Spring crée bien le bean mais ne planifie aucune exécution
 * sans cette annotation. Erreur silencieuse, aucune exception.
 */
@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
