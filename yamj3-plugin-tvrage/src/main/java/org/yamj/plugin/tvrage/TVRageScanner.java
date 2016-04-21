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
package org.yamj.plugin.tvrage;

import static org.yamj.plugin.api.common.Constants.SOURCE_TVRAGE;

import com.omertron.tvrageapi.model.*;
import java.util.Locale;
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
import ro.fortsoft.pf4j.Extension;

@Extension
public final class TVRageScanner implements SeriesScanner {

    private static final Logger LOG = LoggerFactory.getLogger(TVRageScanner.class);

    private PluginConfigService configService;
    private TVRageApiWrapper tvRageApiWrapper;
    private Locale locale;
    
    @Override
    public String getScannerName() {
        return SOURCE_TVRAGE;
    }

    @Override
    public void init(PluginConfigService configService, PluginMetadataService metadataService, PluginLocaleService localeService, CommonHttpClient httpClient) {
        this.configService = configService;
        this.locale = localeService.getLocale();
        this.tvRageApiWrapper = TVRagePlugin.getTVRageApiWrapper();
    }

    @Override
    public String getSeriesId(String title, String originalTitle, int year, Map<String, String> ids, boolean throwTempError) {
        String tvRageId = ids.get(SOURCE_TVRAGE);
        if (StringUtils.isNumeric(tvRageId)) {
            return tvRageId;
        }

        ShowInfo showInfo = null;
        if (StringUtils.isNotBlank(tvRageId)) {
            // try by vanity URL
            showInfo = tvRageApiWrapper.getShowInfoByVanityURL(tvRageId, throwTempError);
        }
        
        // try by title
        if (showInfo == null || !showInfo.isValid()) {
            showInfo = tvRageApiWrapper.getShowInfoByTitle(title, throwTempError);
        }

        // try by original title
        if ((showInfo == null || !showInfo.isValid()) && MetadataTools.isOriginalTitleScannable(title, originalTitle)) {
            showInfo = tvRageApiWrapper.getShowInfoByTitle(originalTitle, throwTempError);
        }

        if (showInfo != null && showInfo.isValid() && showInfo.getShowID()>0) {
            return Integer.toString(showInfo.getShowID());
        }
        
        return null;
    }

    @Override
    public boolean scanSeries(SeriesDTO series, boolean throwTempError) {
        // get series id
        final String tvRageId = series.getIds().get(SOURCE_TVRAGE);
        if (!StringUtils.isNumeric(tvRageId)) {
            return false;
        }

        // set series info
        ShowInfo showInfo = tvRageApiWrapper.getShowInfo(tvRageId, throwTempError);
        if (showInfo == null || !showInfo.isValid()) {
            LOG.error("Can't find informations for TV rage ID '{}'", tvRageId);
            return false;
        }

        // get episodes
        EpisodeList episodeList = tvRageApiWrapper.getEpisodeList(tvRageId, throwTempError);

        // retrieve title
        series.setTitle(showInfo.getShowName());
        if (showInfo.getAkas() != null) {
            // try AKAs for title in another country
            loop: for (CountryDetail cd : showInfo.getAkas()) {
                if (locale.getCountry().equalsIgnoreCase(cd.getCountry())) {
                    series.setTitle(cd.getDetail());
                    break loop;
                }
            }
        }
        
        series.setOriginalTitle(showInfo.getShowName())
            .setPlot(showInfo.getSummary())
            .setOutline(showInfo.getSummary())
            .setStartYear(showInfo.getStarted())
            .setEndYear(MetadataTools.extractYearAsInt(showInfo.getEnded()))
            .setGenres(showInfo.getGenres())
            .addCountry(showInfo.getOriginCountry());
            
        for (CountryDetail cd : showInfo.getNetwork()) {
            if (StringUtils.isNotBlank(cd.getDetail())) {
                series.addStudio(cd.getDetail());
            }
        }
        
        scanSeasons(series, episodeList);
        
        return true;
    }
    
    private static void scanSeasons(SeriesDTO series, EpisodeList episodeList) {
        
        for (SeasonDTO season : series.getSeasons()) {
            if (season.isScanNeeded()) {
                // use values from series
                season.addId(SOURCE_TVRAGE, series.getIds().get(SOURCE_TVRAGE))
                    .setTitle(series.getTitle())
                    .setOriginalTitle(series.getOriginalTitle())
                    .setPlot(series.getPlot())
                    .setOutline(series.getOutline());
                
                // get season year from minimal first aired of episodes
                Episode tvEpisode = episodeList.getEpisode(season.getSeasonNumber(), 1);
                if (tvEpisode != null && tvEpisode.getAirDate() != null) {
                    season.setYear(MetadataTools.extractYearAsInt(tvEpisode.getAirDate()));
                }
            }
            
            // scan episodes
            scanEpisodes(season, episodeList);
        }
    }

    private static void scanEpisodes(SeasonDTO season, EpisodeList episodeList) {
        for (EpisodeDTO episode : season.getEpisodes()) {
            // get the episode
            Episode tvEpisode = episodeList.getEpisode(season.getSeasonNumber(), episode.getEpisodeNumber());
            if (tvEpisode == null || !tvEpisode.isValid()) {
                episode.setValid(false);
                continue;
            }
            
            try {
                int lastIdx = StringUtils.lastIndexOf(tvEpisode.getLink(), "/");
                if (lastIdx > 0) {
                    String tvRageId = tvEpisode.getLink().substring(lastIdx+1);
                    episode.addId(SOURCE_TVRAGE, tvRageId);
                }   
            } catch (Exception ex) {/*ignore*/}
            
            episode.setTitle(tvEpisode.getTitle())
                .setPlot(tvEpisode.getSummary())
                .setReleaseDate(tvEpisode.getAirDate())
                .setRating(MetadataTools.parseRating(tvEpisode.getRating()));
        }
    }
    
    @Override
    public boolean scanNFO(String nfoContent, IdMap idMap) {
        // if we already have the ID, skip the scanning of the NFO file
        final boolean ignorePresentId = configService.getBooleanProperty("tvrage.nfo.ignore.present.id", false);
        if (!ignorePresentId && StringUtils.isNotBlank(idMap.getId(SOURCE_TVRAGE))) {
            return true;
        }

        LOG.trace("Scanning NFO for TVRage ID");

        // There are two formats for the URL. The first is a vanity URL with the show name in it,
        // http://www.tvrage.com/House
        // the second is an id based URL
        // http://www.tvrage.com/shows/id-22771

        String text = "/shows/";
        int beginIndex = nfoContent.indexOf(text);
        if (beginIndex > -1) {
            StringTokenizer st = new StringTokenizer(nfoContent.substring(beginIndex + text.length()), "/ \n,:!&é\"'(è_çà)=$");
            // Remove the "id-" from the front of the ID
            String id = st.nextToken().substring("id-".length());
            LOG.debug("TVRage ID found in NFO: {}", id);
            idMap.addId(SOURCE_TVRAGE, id);
            return true;
        }
         
        text = "tvrage.com/";
        beginIndex = nfoContent.indexOf(text);
        if (beginIndex > -1) {
            String id = new StringTokenizer(nfoContent.substring(beginIndex + text.length()), "/ \n,:!&\"'=$").nextToken();
            LOG.debug("TVRage vanity ID found in NFO: {}", id);
            idMap.addId(SOURCE_TVRAGE, id);
            return true;
        }

        LOG.debug("No TVRage ID found in NFO");
        return false;
    }
}
