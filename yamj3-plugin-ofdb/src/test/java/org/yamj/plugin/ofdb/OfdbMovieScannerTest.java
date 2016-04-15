/*
 *      Copyright (c) 2004-2016 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ) plugins.
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3-plugins
 *
 */
package org.yamj.plugin.ofdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamj.api.common.http.HttpClientWrapper;
import org.yamj.api.common.http.SimpleHttpClientBuilder;
import org.yamj.plugin.api.PluginConfigServiceImpl;
import org.yamj.plugin.api.metadata.Movie;
import org.yamj.plugin.api.metadata.MovieScanner;

public class OfdbMovieScannerTest {

    private static MovieScanner movieScanner;
    
    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() {
        movieScanner = new OfdbMovieScanner();
        movieScanner.init(new PluginConfigServiceImpl(), new HttpClientWrapper(new SimpleHttpClientBuilder().build()), Locale.getDefault());
    }

    @Test
    public void testGetMovieId() {
        Map<String,String> ids = new HashMap<>();
        String id = movieScanner.getMovieId("Avatar", null, 2009, ids, false);
        assertEquals("http://www.ofdb.de/film/188514,Avatar---Aufbruch-nach-Pandora", id);
    }

    @Test
    public void testScanMovie() {
        Movie movie = new Movie();
        movie.addId(movieScanner.getScannerName(), "http://www.ofdb.de/film/188514,Avatar---Aufbruch-nach-Pandora");
        boolean result = movieScanner.scanMovie(movie, false);
        
        assertTrue(result);
        assertEquals("Avatar - Aufbruch nach Pandora", movie.getTitle());
        assertEquals(2009, movie.getYear());
        assertEquals("Dem querschnittsgelähmten Kriegsveteranen Jake Sully (Sam Worthington)  wird die Chance offeriert wieder an einem Einsatz teilzunehmen: Auf dem Planeten Pandora gibt es große Vorkommen des wichtigen Rohstoffs Unobtanium. Die Umwelt des Planeten ist jedoch ebenso schön wie tödlich für den Menschen, deshalb wurde an dem Projekt AVTR gearbeitet dessen Ziel es ist menschliche DNA mit dem der Ureinwohner, den Na'vi, zu mischen. So wurden AVaTaRe erschaffen, die es den Menschen ermöglichen sich gefahrlos in der Umwelt des Paneten zu bewegen. Jake, der in seiner Verkörperung als Avatar auch wieder gehen kann, macht schließlich die Bekanntschaft der Na'vi-Prinzessin Neytiri (Zoe Saldana), diese zeigt ihm deren Kultur, Vorlieben und das Leben in Einklang mit der Natur.  Jake muss erkennen, dass die Na'vi nicht die Aggressoren sind als die sie in den Berichten dargestellt wurden, sondern das es im Gegenteil seine eigene Rasse ist, die zunehmend brutal und rücksichtslos gegen die Ureinwohner vorgeht. Als von Jake verlangt wird den Na'vi klar zumachen, dass diese das Gebiet zwecks Abbaus des Unobtaniums räumen müssen wird ihm klar, das er sich für eine Seite entscheiden muss ...", movie.getPlot());
        assertNotNull(movie.getOutline());
        assertTrue(movie.getGenres().contains("Abenteuer"));
        assertTrue(movie.getGenres().contains("Action"));
        assertTrue(movie.getGenres().contains("Science-Fiction"));
    }
}