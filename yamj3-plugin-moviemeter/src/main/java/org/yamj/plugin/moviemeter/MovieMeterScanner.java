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
import java.util.Map;
import java.util.StringTokenizer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.plugin.api.common.PluginConfigService;
import org.yamj.plugin.api.common.PluginLocaleService;
import org.yamj.plugin.api.common.PluginMetadataService;
import org.yamj.plugin.api.metadata.*;
import org.yamj.plugin.api.type.JobType;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class MovieMeterScanner implements MovieScanner {

    private static final Logger LOG = LoggerFactory.getLogger(MovieMeterScanner.class);
    private static final String SCANNER_NAME = "moviemeter";
    
    private PluginConfigService configService;
    private MovieMeterApiWrapper movieMeterApiWrapper;
    
    @Override
    public String getScannerName() {
        return SCANNER_NAME;
    }

    @Override
    public void init(PluginConfigService configService, PluginMetadataService metadataService, PluginLocaleService localeService, CommonHttpClient httpClient) {
        this.configService = configService;
        this.movieMeterApiWrapper = MovieMeterPlugin.getMovieMeterApiWrapper();
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
        if (!StringUtils.isNumeric(movieMeterId) && MetadataTools.isOriginalTitleScannable(title, originalTitle)) {
            movieMeterId = movieMeterApiWrapper.getMovieIdByTitleAndYear(originalTitle, year, throwTempError);
        }

        return movieMeterId;
    }
    
    @Override
    public boolean scanMovie(MovieDTO movie, boolean throwTempError) {
        final String movieMeterId = movie.getIds().get(SCANNER_NAME);
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
                    movie.addCredit(new CreditDTO(SCANNER_NAME, JobType.ACTOR, actor.getName()).setVoice(actor.isVoice()));
                }
            }
        }

        if (configService.isCastScanEnabled(JobType.DIRECTOR)) {
            for (String director : filmInfo.getDirectors()) {
                if (StringUtils.isNotBlank(director)) {
                    movie.addCredit(new CreditDTO(SCANNER_NAME, JobType.DIRECTOR, director));
                }
            }
        }

        return true;
    }

    @Override
    public boolean scanNFO(String nfoContent, IdMap idMap) {
        // if we already have the ID, skip the scanning of the NFO file
        final boolean ignorePresentId = configService.getBooleanProperty("moviemeter.nfo.ignore.present.id", false);
        if (!ignorePresentId && StringUtils.isNumeric(idMap.getId(SCANNER_NAME))) {
            return true;
        }

        LOG.trace("Scanning NFO for MovieMeter ID");

        int beginIndex = nfoContent.indexOf("www.moviemeter.nl/film/");
        if (beginIndex != -1) {
            String id = new StringTokenizer(nfoContent.substring(beginIndex + 23), "/ \n,:!&é\"'(--è_çà)=$").nextToken();
            if (StringUtils.isNumeric(id)) {
                LOG.debug("MovieMeter ID found in NFO: {}", id);
                idMap.addId(SCANNER_NAME, id);
                return true;
            }
        }
        
        LOG.debug("No MovieMeter ID found in NFO");
        return false;
    }
}
