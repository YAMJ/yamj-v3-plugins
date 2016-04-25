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
package org.yamj.plugin.comingsoon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.yamj.api.common.http.HttpClientWrapper;
import org.yamj.api.common.http.SimpleHttpClientBuilder;
import org.yamj.plugin.api.metadata.MovieDTO;
import org.yamj.plugin.api.metadata.MovieScanner;
import org.yamj.plugin.api.service.mock.PluginConfigServiceMock;
import org.yamj.plugin.api.service.mock.PluginLocaleServiceMock;
import org.yamj.plugin.api.service.mock.PluginMetadataServiceMock;

public class ComingSoonMovieScannerTest {

    private static MovieScanner movieScanner;
    
    @BeforeClass
    @SuppressWarnings("resource")
    public static void setUpClass() {
        movieScanner = new ComingSoonMovieScanner();
        movieScanner.init(new PluginConfigServiceMock(), new PluginMetadataServiceMock(), new PluginLocaleServiceMock(), new HttpClientWrapper(new SimpleHttpClientBuilder().build()));
    }

    @Test
    public void testGetMovieId() {
        Map<String,String> ids = Collections.emptyMap();
        String id = movieScanner.getMovieId("Avatar", null, 2009, ids, false);
        assertEquals("846", id);
    }

    @Test
    public void testScanMovie() {
        Map<String,String> ids = new HashMap<>();
        ids.put(movieScanner.getScannerName(), "846");
        MovieDTO movie = new MovieDTO(ids);
        movieScanner.scanMovie(movie, false);

        assertEquals("Avatar", movie.getTitle());
        assertEquals("Avatar", movie.getOriginalTitle());
        assertEquals(2009, movie.getYear());
        assertEquals("Entriamo in questo mondo alieno attraverso gli occhi di Jake Sully, un ex Marine costretto a vivere sulla sedia a rotelle. Nonostante il suo corpo martoriato, Jake nel profondo è ancora un combattente. E' stato reclutato per viaggiare anni luce sino all'avamposto umano su Pandora, dove alcune società stanno estraendo un raro minerale che è la chiave per risolvere la crisi energetica sulla Terra. Poiché l'atmosfera di Pandora è tossica, è stato creato il Programma Avatar, in cui i \"piloti\" umani collegano le loro coscienze ad un avatar, un corpo organico controllato a distanza che può sopravvivere nell'atmosfera letale. Questi avatar sono degli ibridi geneticamente sviluppati dal DNA umano unito al DNA dei nativi di Pandora... i Na’vi.Rinato nel suo corpo di Avatar, Jake può camminare nuovamente. Gli viene affidata la missione di infiltrarsi tra i Na'vi che sono diventati l'ostacolo maggiore per l'estrazione del prezioso minerale. Ma una bellissima donna Na'vi, Neytiri, salva la vita a Jake, e questo cambia tutto.", movie.getPlot());
        assertNotNull(movie.getOutline());
        assertTrue(movie.getGenres().contains("Fantascienza"));
        assertTrue(movie.getGenres().contains("Avventura"));
        assertTrue(movie.getGenres().contains("Azione"));
        assertTrue(movie.getGenres().contains("Thriller"));
    }
}