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
package org.yamj.plugin.themoviedb;

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;
import static org.yamj.plugin.api.Constants.SOURCE_TMDB;
import static org.yamj.plugin.api.metadata.MetadataTools.isOriginalTitleScannable;
import static org.yamj.plugin.api.metadata.MetadataTools.parseToDate;

import com.omertron.themoviedbapi.model.movie.MovieInfo;
import java.util.Date;
import java.util.Locale;
import java.util.StringTokenizer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.NeedsConfigService;
import org.yamj.plugin.api.NeedsLocaleService;
import org.yamj.plugin.api.NeedsMetadataService;
import org.yamj.plugin.api.metadata.NfoScanner;
import org.yamj.plugin.api.model.*;
import org.yamj.plugin.api.model.type.JobType;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.service.PluginLocaleService;
import org.yamj.plugin.api.service.PluginMetadataService;
 
public abstract class AbstractTheMovieDbScanner implements NfoScanner, NeedsConfigService, NeedsLocaleService, NeedsMetadataService {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractTheMovieDbScanner.class);

    protected PluginConfigService configService;
    protected PluginMetadataService metadataService;
    protected PluginLocaleService localeService;
    protected TheMovieDbApiWrapper theMovieDbApiWrapper;

    @Override
    public final String getScannerName() {
        return SOURCE_TMDB;
    }

    @Override
    public final void setConfigService(PluginConfigService configService) {
        this.configService = configService;
        // also set the API wrapper
        this.theMovieDbApiWrapper = TheMovieDbPlugin.getTheMovieDbApiWrapper();
    }

    @Override
    public final void setLocaleService(PluginLocaleService localeService) {
        this.localeService = localeService;
    }

    @Override
    public final void setMetadataService(PluginMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public boolean scanNFO(String nfoContent, IdMap idMap) {
        if (configService.getBooleanProperty("themoviedb.search.imdb", false)) {
            metadataService.scanNFO(SOURCE_IMDB, nfoContent, idMap);
        }

        // if we already have the ID, skip the scanning of the NFO file
        final boolean ignorePresentId = configService.getBooleanProperty("themoviedb.nfo.ignore.present.id", false);
        if (!ignorePresentId && isValidTheMovieDbId(idMap.getId(SOURCE_TMDB))) {
            return true;
        }

        LOG.trace("Scanning NFO for TheMovieDb ID");

        try {
            int beginIndex = nfoContent.indexOf("/movie/");
            if (beginIndex != -1) {
                String tmdbId = new StringTokenizer(nfoContent.substring(beginIndex + 7), "/ \n,:!&é\"'(--è_çà)=$").nextToken();
                if (isValidTheMovieDbId(tmdbId)) {
                    LOG.debug("TheMovieDb ID found in NFO: {}", tmdbId);
                    idMap.addId(SOURCE_TMDB, tmdbId);
                    return true;
                }
            }
        } catch (Exception ex) {
            LOG.trace("NFO scanning error", ex);
        }

        LOG.debug("No TheMovieDb ID found in NFO");
        return false;
    }

    public String getMovieId(IMovie movie, boolean throwTempError) {
        String tmdbId = movie.getId(SOURCE_TMDB);
        if (isValidTheMovieDbId(tmdbId)) {
            return tmdbId;
        }

        int id = -1;
        String imdbId = movie.getId(SOURCE_IMDB);
        Locale locale = localeService.getLocale();
        
        if (StringUtils.isNotBlank(imdbId)) {
            // Search based on IMDb ID
            LOG.debug("Using IMDb id {} for '{}'", imdbId, movie.getTitle());
            MovieInfo movieInfo = theMovieDbApiWrapper.getMovieInfoByIMDB(imdbId, locale, throwTempError);
            if (movieInfo != null) {
                id = movieInfo.getId();
            }
        }

        if (id<0) {
            LOG.debug("No TMDb id found for '{}', searching title with year {}", movie.getTitle(), movie.getYear());
            id = theMovieDbApiWrapper.getMovieId(movie.getTitle(), movie.getYear(), locale, throwTempError);
        }

        if (id<0 && isOriginalTitleScannable(movie)) {
            LOG.debug("No TMDb id found for '{}', searching original title with year {}", movie.getTitle(), movie.getYear());
            id = theMovieDbApiWrapper.getMovieId(movie.getOriginalTitle(), movie.getYear(), locale, throwTempError);
        }

        if (id > 0) {
            tmdbId = Integer.toString(id);
            movie.addId(SOURCE_TMDB, tmdbId);
            return tmdbId;
        }
        return null;
    }

    public String getSeriesId(ISeries series, boolean throwTempError) {
        String tmdbId = series.getId(SOURCE_TMDB);
        if (isValidTheMovieDbId(tmdbId)) {
            return tmdbId;
        }

        Locale locale = localeService.getLocale();

        LOG.debug("No TMDb id found for '{}', searching title with year {}", series.getTitle(), series.getStartYear());
        int id = theMovieDbApiWrapper.getSeriesId(series.getTitle(), series.getStartYear(), locale, throwTempError);

        if (id<0 && isOriginalTitleScannable(series)) {
            LOG.debug("No TMDb id found for '{}', searching original title with year {}", series.getTitle(), series.getStartYear());
            id = theMovieDbApiWrapper.getSeriesId(series.getOriginalTitle(), series.getStartYear(), locale, throwTempError);
        }

        if (id > 0) {
            tmdbId = Integer.toString(id);
            series.addId(SOURCE_TMDB, tmdbId);
            return tmdbId;
        }
        return null;
    }

    public String getPersonId(IPerson person, boolean throwTempError) {
        String tmdbId = person.getId(SOURCE_TMDB);
        if (isValidTheMovieDbId(tmdbId)) {
            return tmdbId;
        }

        int id = theMovieDbApiWrapper.getPersonId(person.getName(), throwTempError);

        if (id > 0) {
            tmdbId = Integer.toString(id);
            person.addId(SOURCE_TMDB, tmdbId);
            return tmdbId;
        }
        return null;
    }

    protected static boolean isValidTheMovieDbId(String tmdbId) {
        return StringUtils.isNumeric(tmdbId);
    }

    protected static boolean isNoValidTheMovieDbId(String tmdbId) {
        return !isValidTheMovieDbId(tmdbId);
    }

    protected static Date parseTMDbDate(String date) {
        if (StringUtils.isNotBlank(date) && !"1900-01-01".equals(date)) {
            return parseToDate(date);
        }
        return null;
    }

    protected static JobType retrieveJobType(String department) { //NOSONAR
        if (StringUtils.isBlank(department)) {
            return JobType.UNKNOWN;
        }

        switch (department.toLowerCase()) {
            case "writing":
                return JobType.WRITER;
            case "directing":
                return JobType.DIRECTOR;
            case "production":
            case "creator":
                return JobType.PRODUCER;
            case "sound":
                return JobType.SOUND;
            case "camera":
                return JobType.CAMERA;
            case "art":
                return JobType.ART;
            case "editing":
                return JobType.EDITING;
            case "costume & make-up":
                return JobType.COSTUME_MAKEUP;
            case "crew":
                return JobType.CREW;
            case "visual effects":
                return JobType.EFFECTS;
            case "lighting":
                return JobType.LIGHTING;
            default:
                LOG.debug("Unknown department '{}'", department);
                return JobType.UNKNOWN;
        }
    }
}

