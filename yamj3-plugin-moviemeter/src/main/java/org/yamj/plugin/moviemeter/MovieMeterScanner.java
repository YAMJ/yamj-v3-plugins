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
package org.yamj.plugin.moviemeter;

import com.omertron.moviemeter.model.Actor;
import com.omertron.moviemeter.model.FilmInfo;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.plugin.api.PluginConfigService;
import org.yamj.plugin.api.metadata.Credit;
import org.yamj.plugin.api.metadata.Movie;
import org.yamj.plugin.api.metadata.MovieScanner;
import org.yamj.plugin.api.type.JobType;

public class MovieMeterScanner implements MovieScanner {

    private static final Logger LOG = LoggerFactory.getLogger(MovieMeterScanner.class);
    private static final String SCANNER_NAME = "moviemeter";
    
    private PluginConfigService configService;
    private MovieMeterApiWrapper movieMeterApiWrapper;
    
    @Override
    public String getScannerName() {
        return SCANNER_NAME;
    }

    @Override
    public void init(PluginConfigService configService, CommonHttpClient httpClient, Locale locale) {
        this.configService = configService;
        this.movieMeterApiWrapper = MovieMeterApiWrapper.getInstance();
    }
    
    @Override
    public String getMovieId(String title, String originalTitle, int year, Map<String, String> ids, boolean throwTempError) {
        String movieMeterId = ids.get(SCANNER_NAME);
        if (StringUtils.isNumeric(movieMeterId)) {
            return movieMeterId;
        }
        
        // try to get the MovieMeter ID using the IMDB ID
        String imdbId = ids.get("imdb");
        if (StringUtils.isNotBlank(imdbId)) {
            movieMeterId = movieMeterApiWrapper.getMovieIdByIMDbId(imdbId, throwTempError);
        }

        // try to get the MovieMeter ID using title and year
        if (!StringUtils.isNumeric(movieMeterId)) {
            movieMeterId = movieMeterApiWrapper.getMovieIdByTitleAndYear(title, year, throwTempError);
        }

        // try to get the MovieMeter ID using original title and year
        if (!StringUtils.isNumeric(movieMeterId) && StringUtils.isNotBlank(originalTitle) && !StringUtils.equalsIgnoreCase(title, originalTitle)) {
            movieMeterId = movieMeterApiWrapper.getMovieIdByTitleAndYear(originalTitle, year, throwTempError);
        }
        
        // add id in id map
        ids.put(SCANNER_NAME, movieMeterId);
        return movieMeterId;
    }
    
    @Override
    public boolean scanMovie(Movie movie, boolean throwTempError) {
        final String movieMeterId = movie.getIds().get(getScannerName());
        if (!StringUtils.isNumeric(movieMeterId)) {
            return false;
        }

        // get movie info 
        FilmInfo filmInfo = movieMeterApiWrapper.getFilmInfo(movieMeterId, throwTempError);
        if (filmInfo == null) {
            LOG.error("Can't find informations for moviemeter ID '{}'", movieMeterId);
            return false;
        }

        // set IMDb id
        movie.addId("imdb", filmInfo.getImdbId())
            .setRating(Math.round(filmInfo.getAverage() * 20f))
            .setTitle(filmInfo.getDisplayTitle())
            .setOriginalTitle(filmInfo.getAlternativeTitle())
            .setYear(filmInfo.getYear())
            .setPlot(filmInfo.getPlot())
            .setOutline(filmInfo.getPlot())
            .setCountries(filmInfo.getCountries())
            .setGenres(filmInfo.getGenres());

        if (configService.isCastScanEnabled(JobType.ACTOR)) {
            for (Actor actor : filmInfo.getActors()) {
                if (StringUtils.isNotBlank(actor.getName())) {
                    movie.addCredit(new Credit(JobType.ACTOR, actor.getName()).setVoice(actor.isVoice()));
                }
            }
        }

        if (configService.isCastScanEnabled(JobType.DIRECTOR)) {
            for (String director : filmInfo.getDirectors()) {
                if (StringUtils.isNotBlank(director)) {
                    movie.addCredit(new Credit(JobType.DIRECTOR, director));
                }
            }
        }

        return true;
    }

    @Override
    public String scanNFO(String nfoContent) {
        int beginIndex = nfoContent.indexOf("www.moviemeter.nl/film/");
        if (beginIndex != -1) {
            return new StringTokenizer(nfoContent.substring(beginIndex + 23), "/ \n,:!&é\"'(--è_çà)=$").nextToken();
        }
        return null;
    }
}
