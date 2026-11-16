package sn.terrainapp.backend.integration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import sn.terrainapp.backend.sms.EnvoiSms;

/** SMS des tests : gardes en memoire (dernier message par numero) au lieu d'etre envoyes. */
@TestConfiguration
public class SmsDeTest {

    public static final Map<String, String> DERNIERS = new ConcurrentHashMap<>();

    @Bean
    @Primary
    EnvoiSms smsDeTest() {
        return DERNIERS::put;
    }
}
