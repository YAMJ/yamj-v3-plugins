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

import static org.yamj.plugin.api.Constants.SOURCE_TVRAGE;

import com.omertron.tvrageapi.model.*;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.SeriesScanner;
import org.yamj.plugin.api.model.*;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.service.PluginLocaleService;
import org.yamj.plugin.api.service.PluginMetadataService;
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
    public boolean isValidSeriesId(String seriesId) {
        return StringUtils.isNumeric(seriesId);
    }

    @Override
    public String getSeriesId(ISeries series, boolean throwTempError) {
        String tvRageId = series.getId(SOURCE_TVRAGE);
        if (isValidSeriesId(tvRageId)) {
            return tvRageId;
        }

        ShowInfo showInfo = null;
        if (StringUtils.isNotBlank(tvRageId)) {
            // try by vanity URL
            showInfo = tvRageApiWrapper.getShowInfoByVanityURL(tvRageId, throwTempError);
        }
        
        // try by title
        if (showInfo == null || !showInfo.isValid()) {
            showInfo = tvRageApiWrapper.getShowInfoByTitle(series.getTitle(), throwTempError);
        }

        // try by original title
        if ((showInfo == null || !showInfo.isValid()) && MetadataTools.isOriginalTitleScannable(series.getTitle(), series.getOriginalTitle())) {
            showInfo = tvRageApiWrapper.getShowInfoByTitle(series.getOriginalTitle(), throwTempError);
        }

        if (showInfo != null && showInfo.isValid() && showInfo.getShowID()>0) {
            return Integer.toString(showInfo.getShowID());
        }
        
        return null;
    }

    @Override
    public boolean scanSeries(ISeries series, boolean throwTempError) {
        // get series id
        final String tvRageId = series.getId(SOURCE_TVRAGE);
        if (!isValidSeriesId(tvRageId)) {
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
        String title = showInfo.getShowName();
        if (showInfo.getAkas() != null) {
            // try AKAs for title in another country
            loop: for (CountryDetail cd : showInfo.getAkas()) {
                if (locale.getCountry().equalsIgnoreCase(cd.getCountry())) {
                    title = cd.getDetail();
                    break loop;
                }
            }
        }

        series.setTitle(title);
        series.setOriginalTitle(showInfo.getShowName());
        series.setPlot(showInfo.getSummary());
        series.setOutline(showInfo.getSummary());
        series.setStartYear(showInfo.getStarted());
        series.setEndYear(MetadataTools.extractYearAsInt(showInfo.getEnded()));
        series.setGenres(showInfo.getGenres());
        
        if (StringUtils.isNotBlank(showInfo.getOriginCountry())) {
            Set<String> countries = Collections.singleton(showInfo.getOriginCountry());
            series.setCountries(countries);
        }
        
        for (CountryDetail cd : showInfo.getNetwork()) {
            Set<String> studios = new HashSet<>(showInfo.getNetwork().size());
            if (StringUtils.isNotBlank(cd.getDetail())) {
                studios.add(cd.getDetail());
            }
            series.setStudios(studios);
        }
        
        scanSeasons(series, title, showInfo, episodeList);
        
        return true;
    }
    
    private static void scanSeasons(ISeries series, String title, ShowInfo showInfo, EpisodeList episodeList) {
        
        for (ISeason season : series.getSeasons()) {
            
            // nothing to do if season already done
            if (!season.isDone()) {
                // use values from series
                season.addId(SOURCE_TVRAGE, series.getId(SOURCE_TVRAGE));
                season.setTitle(title);
                season.setOriginalTitle(showInfo.getShowName());
                season.setPlot(showInfo.getSummary());
                season.setOutline(showInfo.getSummary());
                
                // get season year from minimal first aired of episodes
                Episode tvRageEpisode = episodeList.getEpisode(season.getNumber(), 1);
                if (tvRageEpisode != null && tvRageEpisode.getAirDate() != null) {
                    season.setYear(MetadataTools.extractYearAsInt(tvRageEpisode.getAirDate()));
                }
            }
            
            // scan episodes
            scanEpisodes(season, episodeList);
        }
    }

    private static void scanEpisodes(ISeason season, EpisodeList episodeList) {
        for (IEpisode episode : season.getEpisodes()) {
            if (episode.isDone()) {
                // nothing to do anymore
                continue;
            }
            
            Episode tvRageEpisode = episodeList.getEpisode(season.getNumber(), episode.getNumber());
            if (tvRageEpisode == null || !tvRageEpisode.isValid()) {
                // mark episode as not found
                episode.setNotFound();
                continue;
            }
            
            try {
                int lastIdx = StringUtils.lastIndexOf(tvRageEpisode.getLink(), "/");
                if (lastIdx > 0) {
                    String tvRageId = tvRageEpisode.getLink().substring(lastIdx+1);
                    episode.addId(SOURCE_TVRAGE, tvRageId);
                }   
            } catch (Exception ex) {/*ignore*/}
            
            episode.setTitle(tvRageEpisode.getTitle());
            episode.setPlot(tvRageEpisode.getSummary());
            episode.setRelease(tvRageEpisode.getAirDate());
            episode.setRating(MetadataTools.parseRating(tvRageEpisode.getRating()));
            
            // mark episode as done
            episode.setDone();
        }
    }
    
    @Override
    public boolean scanNFO(String nfoContent, IdMap idMap) {
        // if we already have the ID, skip the scanning of the NFO file
        final boolean ignorePresentId = configService.getBooleanProperty("tvrage.nfo.ignore.present.id", false);
        if (!ignorePresentId && isValidSeriesId(idMap.getId(SOURCE_TVRAGE))) {
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
