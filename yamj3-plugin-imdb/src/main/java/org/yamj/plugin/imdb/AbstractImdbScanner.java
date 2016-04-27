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
package org.yamj.plugin.imdb;

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;

import java.util.Locale;
import java.util.StringTokenizer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.NeedsConfigService;
import org.yamj.plugin.api.NeedsLocaleService;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.NfoScanner;
import org.yamj.plugin.api.model.*;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.service.PluginLocaleService;
 
public abstract class AbstractImdbScanner implements NfoScanner, NeedsConfigService, NeedsLocaleService {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractImdbScanner.class);

    protected PluginConfigService configService;
    protected ImdbApiWrapper imdbApiWrapper;
    private ImdbSearchEngine imdbSearchEngine;
    protected Locale locale;

    @Override
    public final String getScannerName() {
        return SOURCE_IMDB;
    }

    @Override
    public final void setConfigService(PluginConfigService configService) {
        this.configService = configService;
        // also set the API wrapper
        this.imdbApiWrapper = ImdbPlugin.getImdbApiWrapper();
        this.imdbSearchEngine = ImdbPlugin.getImdbSearchEngine();
    }

    @Override
    public final void setLocaleService(PluginLocaleService localeService) {
        this.locale = localeService.getLocale();
    }

    @Override
    public boolean scanNFO(String nfoContent, IdMap idMap) {
        // if we already have the ID, skip the scanning of the NFO file
        final boolean ignorePresentId = configService.getBooleanProperty("imdb.nfo.ignore.present.id", false);
        // if we already have the ID, skip the scanning of the NFO file
        if (!ignorePresentId && isValidImdbId(idMap.getId(SOURCE_IMDB))) {
            return true;
        }

        LOG.trace("Scanning NFO for IMDb ID");

        try {
            int beginIndex = nfoContent.indexOf("/tt");
            if (beginIndex != -1) {
                String imdbId = new StringTokenizer(nfoContent.substring(beginIndex + 1), "/ \n,:!&ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©\"'(--ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨_ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â )=$").nextToken();
                LOG.debug("IMDb ID found in NFO: {}", imdbId);
                idMap.addId(SOURCE_IMDB, imdbId);
                return true;
            }

            beginIndex = nfoContent.indexOf("/Title?");
            if (beginIndex != -1 && beginIndex + 7 < nfoContent.length()) {
                String imdbId = "tt" + new StringTokenizer(nfoContent.substring(beginIndex + 7), "/ \n,:!&ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©\"'(--ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¨_ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â§ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â )=$").nextToken();
                LOG.debug("IMDb ID found in NFO: {}", imdbId);
                idMap.addId(SOURCE_IMDB, imdbId);
                return true;
            }
        } catch (Exception ex) {
            LOG.trace("NFO scanning error", ex);
        }

        LOG.debug("No IMDb ID found in NFO");
        return false;
    }

    public String getMovieId(IMovie movie, boolean throwTempError) {
        String imdbId = movie.getId(SOURCE_IMDB);

        // search by title
        if (isNoValidImdbId(imdbId)) {
            imdbId = imdbSearchEngine.getImdbId(movie.getTitle(), movie.getYear(), false, throwTempError);
            movie.addId(SOURCE_IMDB, imdbId);
        }
        
        // search by original title
        if (isNoValidImdbId(imdbId) && MetadataTools.isOriginalTitleScannable(movie.getTitle(), movie.getOriginalTitle())) {
            imdbId = imdbSearchEngine.getImdbId(movie.getOriginalTitle(), movie.getYear(), false, throwTempError);
            movie.addId(SOURCE_IMDB, imdbId);
        }
        
        return imdbId;
    }

    public String getSeriesId(ISeries series, boolean throwTempError) {
        String imdbId = series.getId(SOURCE_IMDB);
        if (isNoValidImdbId(imdbId)) {
            imdbId = imdbSearchEngine.getImdbId(series.getTitle(), series.getStartYear(), true, throwTempError);
            series.addId(SOURCE_IMDB, imdbId);
        }
        return imdbId;
    }

    public String getPersonId(IPerson person, boolean throwTempError) {
        String imdbId = person.getId(SOURCE_IMDB);
        if (isValidImdbId(imdbId)) {
            return imdbId;
        }
        if (StringUtils.isNotBlank(person.getName())) {
            imdbId = this.imdbSearchEngine.getImdbPersonId(person.getName(), throwTempError);
            person.addId(SOURCE_IMDB, imdbId);
        }
        return imdbId;
    }

    protected static boolean isValidImdbId(String imdbId) {
        return StringUtils.isNotBlank(imdbId);
    }

    protected static boolean isNoValidImdbId(String imdbId) {
        return StringUtils.isBlank(imdbId);
    }
}

