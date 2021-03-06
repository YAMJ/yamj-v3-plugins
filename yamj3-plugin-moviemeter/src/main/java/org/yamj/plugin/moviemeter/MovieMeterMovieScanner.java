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

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;
import static org.yamj.plugin.moviemeter.MovieMeterPlugin.SCANNER_NAME;

import com.omertron.moviemeter.model.Actor;
import com.omertron.moviemeter.model.FilmInfo;
import java.util.StringTokenizer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.MovieScanner;
import org.yamj.plugin.api.model.IMovie;
import org.yamj.plugin.api.model.IdMap;
import org.yamj.plugin.api.model.type.JobType;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class MovieMeterMovieScanner extends AbstractMovieMeterScanner implements MovieScanner {

    private static final Logger LOG = LoggerFactory.getLogger(MovieMeterMovieScanner.class);
    
    @Override
    public boolean scanMovie(IMovie movie, boolean throwTempError) {
        final String movieMeterId = movie.getId(SCANNER_NAME);
        if (!isValidMovieId(movieMeterId)) {
            LOG.debug("Moviemeter id not available '{}'", movie.getTitle());
            return false;
        }

        // get movie info 
        FilmInfo filmInfo = movieMeterApiWrapper.getFilmInfo(movieMeterId, throwTempError);
        if (filmInfo == null) {
            LOG.error("Can't find informations for movie '{}'", movie.getTitle());
            return false;
        }

        // set IMDb id
        movie.addId(SOURCE_IMDB, filmInfo.getImdbId());
        movie.setRating(Math.round(filmInfo.getAverage() * 20f));
        movie.setTitle(filmInfo.getDisplayTitle());
        movie.setOriginalTitle(filmInfo.getAlternativeTitle());
        movie.setYear(filmInfo.getYear());
        movie.setPlot(filmInfo.getPlot());
        movie.setOutline(filmInfo.getPlot());
        movie.setCountries(filmInfo.getCountries());
        movie.setGenres(filmInfo.getGenres());

        if (configService.isCastScanEnabled(JobType.ACTOR)) {
            for (Actor actor : filmInfo.getActors()) {
                if (StringUtils.isNotBlank(actor.getName())) {
                    movie.addCredit(JobType.ACTOR, actor.getName(), null, actor.isVoice());
                }
            }
        }

        if (configService.isCastScanEnabled(JobType.DIRECTOR)) {
            for (String director : filmInfo.getDirectors()) {
                if (StringUtils.isNotBlank(director)) {
                    movie.addCredit(JobType.DIRECTOR, director);
                }
            }
        }

        return true;
    }

    @Override
    public boolean scanNFO(String nfoContent, IdMap idMap) {
        // if we already have the ID, skip the scanning of the NFO file
        final boolean ignorePresentId = configService.getBooleanProperty("moviemeter.nfo.ignore.present.id", false);
        if (!ignorePresentId && isValidMovieId(idMap.getId(SCANNER_NAME))) {
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
