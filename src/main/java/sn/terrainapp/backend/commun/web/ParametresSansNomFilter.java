package sn.terrainapp.backend.commun.web;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Ignore les parametres de requete sans nom ("?=", "?=x&date=..."). Spring
 * echoue sur un nom vide en liant les @RequestParam (erreur 500 relevee par
 * le scan DAST) : ces parametres n'ont aucun sens, on les retire avant.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ParametresSansNomFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        if (!requete.getParameterMap().containsKey("")) {
            chaine.doFilter(requete, reponse);
            return;
        }
        Map<String, String[]> nommes = new LinkedHashMap<>(requete.getParameterMap());
        nommes.remove("");
        Map<String, String[]> parametres = Collections.unmodifiableMap(nommes);
        chaine.doFilter(new HttpServletRequestWrapper(requete) {
            @Override
            public Map<String, String[]> getParameterMap() {
                return parametres;
            }

            @Override
            public Enumeration<String> getParameterNames() {
                return Collections.enumeration(parametres.keySet());
            }

            @Override
            public String[] getParameterValues(String nom) {
                return parametres.get(nom);
            }

            @Override
            public String getParameter(String nom) {
                String[] valeurs = parametres.get(nom);
                return valeurs == null || valeurs.length == 0 ? null : valeurs[0];
            }
        }, reponse);
    }
}
